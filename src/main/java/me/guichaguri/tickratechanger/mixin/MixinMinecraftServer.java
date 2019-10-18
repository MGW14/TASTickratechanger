package me.guichaguri.tickratechanger.mixin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.FutureTask;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import me.guichaguri.tickratechanger.TickrateChanger;
import net.minecraft.crash.CrashReport;
import net.minecraft.network.ServerStatusResponse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ReportedException;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
	@Shadow
	public abstract boolean init();
	@Shadow
	private long currentTime;
	@Shadow
	private ServerStatusResponse statusResponse;
	@Shadow
	public abstract void applyServerIconToResponse(ServerStatusResponse response);
	@Shadow
	private boolean serverRunning;
	@Shadow
	private String motd;
	@Shadow
	private long timeOfLastWarning;
	@Shadow
	private WorldServer[] worlds;
	@Shadow
	public abstract void tick();
	@Shadow
	private boolean serverIsRunning;
	@Shadow
	public abstract void finalTick(CrashReport report);
	@Shadow
	public abstract CrashReport addServerInfoToCrashReport(CrashReport report);
	@Shadow
	public abstract File getDataDirectory();
	@Shadow
	public abstract void stopServer();
	@Shadow
	private boolean serverStopped;
	@Shadow
	public abstract void systemExitNow();
	@Shadow
	private Queue < FutureTask<? >> futureTaskQueue;
	
	private static long msToTick;
	
	public void run() {
	 try
     {
         if (this.init())
         {
             net.minecraftforge.fml.common.FMLCommonHandler.instance().handleServerStarted();
             this.currentTime = MinecraftServer.getCurrentTimeMillis();
             long i = 0L;
             this.statusResponse.setServerDescription(new TextComponentString(this.motd));
             this.statusResponse.setVersion(new ServerStatusResponse.Version("1.12.2", 340));
             this.applyServerIconToResponse(this.statusResponse);

             while (this.serverRunning)
             {
                 long k = MinecraftServer.getCurrentTimeMillis();
                 long j = k - this.currentTime;

                 if (j > 2000L && this.currentTime - this.timeOfLastWarning >= 15000L)
                 {
                     TickrateChanger.LOGGER.warn("Can't keep up! Did the system time change, or is the server overloaded? Running {}ms behind, skipping {} tick(s)", Long.valueOf(j), Long.valueOf(j / 50L));
                     j = 2000L;
                     this.timeOfLastWarning = this.currentTime;
                 }

                 if (j < 0L)
                 {
                	 TickrateChanger.LOGGER.warn("Time ran backwards! Did the system time change?");
                     j = 0L;
                 }

                 i += j;
                 this.currentTime = k;

                 if (this.worlds[0].areAllPlayersAsleep())
                 {
                     this.tick();
                     i = 0L;
                 }
                 else
                 {
                     while (i > TickrateChanger.MILISECONDS_PER_TICK)
                     {
                         i -= TickrateChanger.MILISECONDS_PER_TICK;
                         this.tick();
                     }
                 }

                 //Added Methods similar to Cubitick Mod
                 //Original line Thread.sleep(Math.max(1L, 50L - i));
					msToTick = (long) (TickrateChanger.MILISECONDS_PER_TICK - i);
					if (msToTick <= 0L) {
						if (TickrateChanger.TICKS_PER_SECOND > 20.0)
							msToTick = 0L;
						else
							msToTick = 1L;
					}
					synchronized (this.futureTaskQueue) {
						while (!this.futureTaskQueue.isEmpty()) {
							try {
								TickrateChanger.LOGGER.debug("Processing Future Task Queue");
								((FutureTask) this.futureTaskQueue.poll()).run();
							} catch (Throwable var9) {
								var9.printStackTrace();
							}
						}
					}
					for (long o = 0; o < msToTick; o++) {
						if (TickrateChanger.INTERRUPT) {
							TickrateChanger.LOGGER.info("Interrupting " + o + " " + msToTick);
							msToTick = 1L;
							currentTime = System.currentTimeMillis();
							TickrateChanger.INTERRUPT = false;
						}
						try {
							Thread.sleep(1L);
						} catch (InterruptedException e) {
							TickrateChanger.LOGGER.error("Thread.sleep in MixinMinecraft couldn't be processed!");
							TickrateChanger.LOGGER.catching(e);
						}
					}
                 this.serverIsRunning = true;
             }
             net.minecraftforge.fml.common.FMLCommonHandler.instance().handleServerStopping();
             net.minecraftforge.fml.common.FMLCommonHandler.instance().expectServerStopped(); // has to come before finalTick to avoid race conditions
         }
         else
         {
             net.minecraftforge.fml.common.FMLCommonHandler.instance().expectServerStopped(); // has to come before finalTick to avoid race conditions
             this.finalTick((CrashReport)null);
         }
     }
     catch (net.minecraftforge.fml.common.StartupQuery.AbortedException e)
     {
         // ignore silently
         net.minecraftforge.fml.common.FMLCommonHandler.instance().expectServerStopped(); // has to come before finalTick to avoid race conditions
     }
     catch (Throwable throwable1)
     {
         TickrateChanger.LOGGER.error("Encountered an unexpected exception", throwable1);
         CrashReport crashreport = null;

         if (throwable1 instanceof ReportedException)
         {
             crashreport = this.addServerInfoToCrashReport(((ReportedException)throwable1).getCrashReport());
         }
         else
         {
             crashreport = this.addServerInfoToCrashReport(new CrashReport("Exception in server tick loop", throwable1));
         }

         File file1 = new File(new File(this.getDataDirectory(), "crash-reports"), "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");

         if (crashreport.saveToFile(file1))
         {
             TickrateChanger.LOGGER.error("This crash report has been saved to: {}", (Object)file1.getAbsolutePath());
         }
         else
         {
        	 TickrateChanger.LOGGER.error("We were unable to save this crash report to disk.");
         }

         net.minecraftforge.fml.common.FMLCommonHandler.instance().expectServerStopped(); // has to come before finalTick to avoid race conditions
         this.finalTick(crashreport);
     }
     finally
     {
         try
         {
             this.stopServer();
             this.serverStopped = true;
         }
         catch (Throwable throwable)
         {
             TickrateChanger.LOGGER.error("Exception stopping the server", throwable);
         }
         finally
         {
             net.minecraftforge.fml.common.FMLCommonHandler.instance().handleServerStopped();
             this.serverStopped = true;
             this.systemExitNow();
         }
     }
	}
}