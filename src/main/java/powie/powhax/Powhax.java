package powie.powhax;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import powie.powhax.commands.ClearChat;
import powie.powhax.commands.Coords;
import powie.powhax.commands.Xp;
import powie.powhax.modules.*;

import static powie.powhax.utils.Config.initializeConfig;

public class Powhax extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("PowHax");
//    public static final HudGroup HUD_GROUP = new HudGroup("PowHax");

    @Override
    public void onInitialize() {
        LOG.info("Initializing PowHax");

        initializeConfig();

        // Modules
        Modules.get().add(new AntiBedTrap());
        Modules.get().add(new AntiTrample());
        Modules.get().add(new ArmorBuster());
        Modules.get().add(new AutoEndorse());
        Modules.get().add(new AutoLogin());
        Modules.get().add(new AutoPearlStasis());
        Modules.get().add(new BedrockSeedfinder());
        Modules.get().add(new DeathCommands());
        Modules.get().add(new FlySpeed());
        Modules.get().add(new HandDerp());
        Modules.get().add(new LavacastHelper());
        Modules.get().add(new PriceScraper());
        Modules.get().add(new SmiteAura());
        Modules.get().add(new TrajectoriesPlus());

        // Commands
        Commands.add(new ClearChat());
        Commands.add(new Coords());
        Commands.add(new Xp());

        // HUD
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getWebsite() {
        return "https://github.com/Powie69/PowHax";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Powie69", "Powhax", "master", null);
    }

    @Override
    public String getPackage() {
        return "powie.powhax";
    }

    @Override
    public String getCommit() {
        String commit = FabricLoader
            .getInstance()
            .getModContainer("powhax")
            .get().getMetadata()
            .getCustomValue("powhax:commit")
            .getAsString();
        return commit.isEmpty() ? null : commit;
    }
}
