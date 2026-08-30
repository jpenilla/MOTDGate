package xyz.jpenilla.motdgate;

import org.bukkit.plugin.java.JavaPlugin;

public final class MOTDGate extends JavaPlugin {
  @Override
  public void onEnable() {
    getLogger().info("MOTDGate enabled - version " + getPluginMeta().getVersion());
  }

  @Override
  public void onDisable() {
    getLogger().info("MOTDGate disabled");
  }
}
