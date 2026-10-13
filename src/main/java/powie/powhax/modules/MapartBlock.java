package powie.powhax.modules;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import powie.powhax.Powhax;
import powie.powhax.settings.StringIntListSetting;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static meteordevelopment.meteorclient.utils.Utils.getFileWorldName;
import static powie.powhax.utils.Config.getMapartFile;
import static powie.powhax.utils.Config.saveMapartFile;

public class MapartBlock extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> maparts = sgGeneral.add(new StringIntListSetting.Builder()
        .name("maparts")
        .description("Map ids to block (saved per world)")
        .defaultValue(List.of(""))
        .onChanged(this::onChanged)
        .build()
    );

    public MapartBlock() {
        super(Powhax.CATEGORY, "mapart-blocker", "Blocks rendering of specified Maparts based on their IDs");
        runInMainMenu = true;
    }

    private void onChanged(List<String> maparts) {
        info(maparts.toString());
    }

    private static void cleanMaps(List<String> maparts) {
        List<String> cleanedMaparts = maparts.stream()
            .filter(s -> !s.isBlank())
            .distinct()
            .filter(s -> {
                try {
                    Integer.parseInt(s);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            })
            .toList();

        maparts.clear();
        maparts.addAll(cleanedMaparts);
    }

    @Override
    public void onActivate() {
        cleanMaps(maparts.get());
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        Set<Integer> mapartFile = getMapartFile(getFileWorldName());
        if (mapartFile.isEmpty()) return;

        maparts.set(mapartFile.stream()
            .map(String::valueOf)
            .toList());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        cleanMaps(maparts.get());
        Set<Integer> mapartSet = maparts.get().stream()
            .map(Integer::parseInt)
            .collect(Collectors.toSet());
        saveMapartFile(getFileWorldName(), mapartSet);
    }

}
