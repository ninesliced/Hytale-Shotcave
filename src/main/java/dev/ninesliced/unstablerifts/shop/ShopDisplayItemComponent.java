package dev.ninesliced.unstablerifts.shop;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * Marks an item entity as a visual shop display prop rather than a pickup,
 * and records which shop room slot it belongs to.
 */
public final class ShopDisplayItemComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, ShopDisplayItemComponent> componentType;
    private int roomAnchorX;
    private int roomAnchorY;
    private int roomAnchorZ;
    private int slotIndex;

    public ShopDisplayItemComponent() {
        this(0, 0, 0, -1);
    }

    public ShopDisplayItemComponent(@Nonnull Vector3i roomAnchor, int slotIndex) {
        this(roomAnchor.x, roomAnchor.y, roomAnchor.z, slotIndex);
    }

    public ShopDisplayItemComponent(int roomAnchorX, int roomAnchorY, int roomAnchorZ, int slotIndex) {
        this.roomAnchorX = roomAnchorX;
        this.roomAnchorY = roomAnchorY;
        this.roomAnchorZ = roomAnchorZ;
        this.slotIndex = slotIndex;
    }

    @Nonnull
    public static ComponentType<EntityStore, ShopDisplayItemComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("ShopDisplayItemComponent has not been registered yet");
        }
        return componentType;
    }

    public static void setComponentType(@Nonnull ComponentType<EntityStore, ShopDisplayItemComponent> type) {
        componentType = type;
    }

    @Nonnull
    public Vector3i getRoomAnchor() {
        return new Vector3i(roomAnchorX, roomAnchorY, roomAnchorZ);
    }

    public boolean matchesRoom(@Nonnull Vector3i roomAnchor) {
        return roomAnchor.x == roomAnchorX
                && roomAnchor.y == roomAnchorY
                && roomAnchor.z == roomAnchorZ;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new ShopDisplayItemComponent(roomAnchorX, roomAnchorY, roomAnchorZ, slotIndex);
    }
}
