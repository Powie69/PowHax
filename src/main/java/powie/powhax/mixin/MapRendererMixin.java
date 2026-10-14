package powie.powhax.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import powie.powhax.modules.MapBlock;

@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
    @Inject(
        method = "extractRenderState",
        at = @At("TAIL")
    )
    private void onExtractRenderState(MapId mapId, MapItemSavedData mapData, MapRenderState mapRenderState, CallbackInfo ci) {
        MapBlock module = Modules.get().get(MapBlock.class);
        if (module == null || !module.isActive()) return;
        if (!module.maps.contains(mapId.id())) return;
        if (!module.replace.get()) mapRenderState.texture = Identifier.fromNamespaceAndPath("sixbees", "transparent.png");
        else mapRenderState.texture = Identifier.fromNamespaceAndPath("powhax", "icon.png");
    }
}
