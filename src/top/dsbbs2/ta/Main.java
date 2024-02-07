package top.dsbbs2.ta;

import io.github.bedwarsrel.*;
import io.github.bedwarsrel.events.BedwarsGameEndEvent;
import io.github.bedwarsrel.events.BedwarsGameOverEvent;
import io.github.bedwarsrel.game.Game;
import io.github.bedwarsrel.game.GameState;
import io.github.bedwarsrel.game.ResourceSpawner;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import top.dsbbs2.ta.config.SimpleConfig;
import top.dsbbs2.ta.config.struct.AccelerationConfig;

import java.lang.reflect.AccessibleObject;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin implements Listener {
    public static final List<Vector> offsets=Arrays.asList(new Vector(0,0,0),new Vector(0,-2,0),new Vector(1,0,0),new Vector(1,-1,0),new Vector(-1,0,0),new Vector(-1,-1,0),new Vector(0,0,1),new Vector(0,-1,1),new Vector(0,0,-1),new Vector(0,-1,-1));
    public final ConcurrentHashMap<ResourceSpawner,Double> acceleratedSpawners=new ConcurrentHashMap<>();
    public final ConcurrentHashMap<ResourceSpawner,Integer> originalInterval=new ConcurrentHashMap<>();
    public final ConcurrentHashMap<ResourceSpawner, BukkitTask> particleTasks=new ConcurrentHashMap<>();
    public volatile SimpleConfig<AccelerationConfig> config=new SimpleConfig<AccelerationConfig>(this.getDataFolder()+"/config.json","UTF8", AccelerationConfig.class){{
        try{
            this.loadConfig();
        }catch(Throwable t){throw new RuntimeException(t);}
    }};
    @Override
    public void onEnable()
    {
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @Override
    public void onDisable()
    {
        this.originalInterval.forEach(ResourceSpawner::setInterval);
        this.originalInterval.keySet().forEach(Main::updateResSpawnTaskPeriod);
        this.originalInterval.clear();
        this.particleTasks.values().forEach(BukkitTask::cancel);
        this.particleTasks.clear();
        this.acceleratedSpawners.clear();
    }
    public static boolean has(Location loc, Material blockType)
    {
        return Main.offsets.stream().map(loc.clone()::add).map(Location::getBlock).map(Block::getType).anyMatch(blockType::equals);
    }
    public static boolean contains(Location resCent, Location changedLoc)
    {
        return Main.offsets.stream().map(resCent.clone()::add).anyMatch(changedLoc::equals);
    }
    @FunctionalInterface
    public static interface ThrowsRunnable extends Runnable {
        void invoke() throws Throwable;
        @Override
        default void run() {
            try{
                this.invoke();
            }catch(Throwable t){throw new RuntimeException(t);}
        }
        static ThrowsRunnable of(ThrowsRunnable r){return r;}
    }
    @FunctionalInterface
    public static interface ThrowsConsumer<T> extends Consumer<T> {
        void consume(T obj) throws Throwable;
        @Override
        default void accept(T obj) {
            try{
                this.consume(obj);
            }catch(Throwable t){throw new RuntimeException(t);}
        }
        static <T> ThrowsConsumer<T> of(ThrowsConsumer<T> r){return r;}
    }
    @FunctionalInterface
    public static interface DefaultNullSupplier<T> extends Supplier<T> {
        T supply() throws Throwable;
        @Override
        default T get() {
            try{
                return this.supply();
            }catch(Throwable t){return null;}
        }
        static <T> DefaultNullSupplier<T> of(DefaultNullSupplier<T> s){return s;}
    }
    public static <T extends AccessibleObject> T makeAccessible(T acc){
        acc.setAccessible(true);
        return acc;
    }
    public static void updateResSpawnTaskPeriod(ResourceSpawner spawner){
        spawner.getGame().getRunningTasks().stream().filter(i->Objects.equals(DefaultNullSupplier.of(()->i.getClass().getField("task").get(i)).get(),spawner)).filter(i->i.getClass().getName().endsWith(".scheduler.CraftTask")).forEach(ThrowsConsumer.of(i->makeAccessible(i.getClass().getDeclaredMethod("setPeriod",long.class)).invoke(i,spawner.getInterval())));
    }
    public void applyTorch(Game game,Location changedLoc)
    {
        if(this.config.getConfig().torch.enable)
            game.getResourceSpawners().stream().filter(i->Main.contains(i.getLocation(),changedLoc)).filter(i->!acceleratedSpawners.containsKey(i)||this.config.getConfig().torch.speed>acceleratedSpawners.get(i)).filter(i->Main.has(i.getLocation(),Material.TORCH)).forEach(i->{
                acceleratedSpawners.put(i,this.config.getConfig().torch.speed);
                i.setInterval((int)Math.round(originalInterval.computeIfAbsent(i,ResourceSpawner::getInterval)/this.config.getConfig().torch.speed));
                updateResSpawnTaskPeriod(i);
                if(this.config.getConfig().particle)
                    particleTasks.computeIfAbsent(i,i2->Bukkit.getScheduler().runTaskTimer(this,()->{
                        if(!Objects.equals(game.getState(), GameState.RUNNING)) {
                            BukkitTask task=particleTasks.get(i2);
                            task.cancel();
                            particleTasks.remove(task);
                            return;
                        }
                        i.getLocation().getWorld().playEffect(i.getLocation(), Effect.VILLAGER_PLANT_GROW,5,1);
                    },0,8));
            });
    }
    public void applyRedstoneTorch(Game game,Location changedLoc)
    {
        if(this.config.getConfig().redstone_torch.enable)
            game.getResourceSpawners().stream().filter(i->Main.contains(i.getLocation(),changedLoc)).filter(i->!acceleratedSpawners.containsKey(i)||this.config.getConfig().redstone_torch.speed>acceleratedSpawners.get(i)).filter(i->Main.has(i.getLocation(),Material.REDSTONE_TORCH_ON)||Main.has(i.getLocation(),Material.REDSTONE_TORCH_OFF)).forEach(i->{
                acceleratedSpawners.put(i,this.config.getConfig().redstone_torch.speed);
                i.setInterval((int)Math.round(originalInterval.computeIfAbsent(i,ResourceSpawner::getInterval)/this.config.getConfig().redstone_torch.speed));
                updateResSpawnTaskPeriod(i);
                if(this.config.getConfig().particle)
                    particleTasks.computeIfAbsent(i,i2->Bukkit.getScheduler().runTaskTimer(this,()->{
                        if(!Objects.equals(game.getState(), GameState.RUNNING)) {
                            BukkitTask task=particleTasks.get(i2);
                            task.cancel();
                            particleTasks.remove(task);
                            return;
                        }
                        i.getLocation().getWorld().playEffect(i.getLocation(), Effect.VILLAGER_PLANT_GROW,5,1);
                    },0,8));
            });
    }
    public void clearByLoc(Game game,Location changedLoc){
        game.getResourceSpawners().stream().filter(i->Main.contains(i.getLocation(),changedLoc)).filter(acceleratedSpawners::containsKey).forEach(i->{
            i.setInterval(originalInterval.get(i));
            updateResSpawnTaskPeriod(i);
            originalInterval.remove(i);
            acceleratedSpawners.remove(i);
            particleTasks.get(i).cancel();
            particleTasks.remove(i);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBlockPlace(BlockPlaceEvent e)
    {
        Game game=BedwarsRel.getInstance().getGameManager().getGameOfPlayer(e.getPlayer());
        if(game!=null){
            applyTorch(game,e.getBlock().getLocation());
            applyRedstoneTorch(game,e.getBlock().getLocation());
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onGameEnd(BedwarsGameEndEvent event)
    {
        this.originalInterval.entrySet().stream().filter(i->Objects.equals(i.getKey().getGame(),event.getGame())).forEach(i->i.getKey().setInterval(i.getValue()));
        this.originalInterval.entrySet().stream().filter(i->Objects.equals(i.getKey().getGame(),event.getGame())).forEach(i->updateResSpawnTaskPeriod(i.getKey()));
        this.originalInterval.entrySet().removeIf(i->Objects.equals(i.getKey().getGame(),event.getGame()));
        this.particleTasks.entrySet().stream().filter(i->Objects.equals(i.getKey().getGame(),event.getGame())).map(Map.Entry::getValue).forEach(BukkitTask::cancel);
        this.particleTasks.entrySet().removeIf(i->Objects.equals(i.getKey().getGame(),event.getGame()));
        this.acceleratedSpawners.entrySet().removeIf(i->Objects.equals(i.getKey().getGame(),event.getGame()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onGameOver(BedwarsGameOverEvent event)
    {
        this.originalInterval.entrySet().stream().filter(i->Objects.equals(i.getKey().getGame(),event.getGame())).forEach(i->i.getKey().setInterval(i.getValue()));
        this.originalInterval.entrySet().stream().filter(i->Objects.equals(i.getKey().getGame(),event.getGame())).forEach(i->updateResSpawnTaskPeriod(i.getKey()));
        this.originalInterval.entrySet().removeIf(i->Objects.equals(i.getKey().getGame(),event.getGame()));
        this.particleTasks.entrySet().stream().filter(i->Objects.equals(i.getKey().getGame(),event.getGame())).map(Map.Entry::getValue).forEach(BukkitTask::cancel);
        this.particleTasks.entrySet().removeIf(i->Objects.equals(i.getKey().getGame(),event.getGame()));
        this.acceleratedSpawners.entrySet().removeIf(i->Objects.equals(i.getKey().getGame(),event.getGame()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent e){
        Game game=BedwarsRel.getInstance().getGameManager().getGameOfPlayer(e.getPlayer());
        if(game!=null) {
            clearByLoc(game, e.getBlock().getLocation());
            applyTorch(game,e.getBlock().getLocation());
            applyRedstoneTorch(game,e.getBlock().getLocation());
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBlockPhysics(BlockPhysicsEvent e){
        Game game=BedwarsRel.getInstance().getGameManager().getGameByLocation(e.getBlock().getLocation());
        if(game!=null) {
            clearByLoc(game,e.getBlock().getLocation());
            applyTorch(game,e.getBlock().getLocation());
            applyRedstoneTorch(game,e.getBlock().getLocation());
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBlockExplosion(BlockExplodeEvent e)
    {
        e.blockList().forEach(i->{
            Game game=BedwarsRel.getInstance().getGameManager().getGameByLocation(i.getLocation());
            if(game!=null) {
                clearByLoc(game,i.getLocation());
                applyTorch(game,i.getLocation());
                applyRedstoneTorch(game,i.getLocation());
            }
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent e)
    {
        Game game=BedwarsRel.getInstance().getGameManager().getGameByLocation(e.getLocation());
        if(game!=null) {
            clearByLoc(game,e.getLocation());
            applyTorch(game,e.getLocation());
            applyRedstoneTorch(game,e.getLocation());
        }
    }
}
