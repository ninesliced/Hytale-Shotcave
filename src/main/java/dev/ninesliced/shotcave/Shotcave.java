package dev.ninesliced.shotcave;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ninesliced.shotcave.camera.TopCameraService;
import dev.ninesliced.shotcave.command.ShotcaveCommand;

import javax.annotation.Nonnull;

public class Shotcave extends JavaPlugin {

    private final TopCameraService cameraService = new TopCameraService();

    public Shotcave(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getEventRegistry().register(PlayerConnectEvent.class, this::onPlayerConnect);
        this.getCommandRegistry().registerCommand(new ShotcaveCommand(this));
    }

    private void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        cameraService.registerDisabledByDefault(playerRef);
    }

    public TopCameraService getCameraService() {
        return cameraService;
    }
}