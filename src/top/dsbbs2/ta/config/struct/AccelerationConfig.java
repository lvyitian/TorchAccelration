package top.dsbbs2.ta.config.struct;

import java.util.UUID;
import java.util.Vector;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class AccelerationConfig {
	public static class TorchLike{
		public TorchLike(){}
		public TorchLike(boolean enable,double speed){this.enable=enable;this.speed=speed;}
		public boolean enable=true;
		public double speed;
	}
	public TorchLike torch=new TorchLike(true,200);
	public TorchLike redstone_torch=new TorchLike(true,300);
	public boolean particle=true;
}