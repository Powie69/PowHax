package powie.powhax;

import powie.powhax.commands.CommandExample;
import powie.powhax.hud.HudExample;
import powie.powhax.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Powhax extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("PowHax");
    public static final HudGroup HUD_GROUP = new HudGroup("PowHax");

    @Override
    public void onInitialize() {
        LOG.info("Initializing PowHax");

        // Modules
//        Modules.get().add(new ModuleExample());
        Modules.get().add(new AntiVanish());
        Modules.get().add(new AutoSell());
        Modules.get().add(new DimensionGodMode());
        Modules.get().add(new BlazeFarm());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "powie.powhax";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
