package dev.ninesliced.unstablerifts.mission;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.InstructionType;
import com.hypixel.hytale.server.npc.asset.builder.holder.AssetHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;
import java.util.EnumSet;

/**
 * NPC action builder that reads the {@code "Shop"} config key from the role JSON
 * and produces an {@link ActionOpenRiftMerchant} action.
 */
public final class BuilderActionOpenRiftMerchant extends BuilderActionBase {

    @Nonnull
    private final AssetHolder shopId = new AssetHolder();

    public BuilderActionOpenRiftMerchant() {
    }

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Open the Rift Merchant missions and shop page";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return getShortDescription();
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionOpenRiftMerchant(this, builderSupport);
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public BuilderActionOpenRiftMerchant readConfig(@Nonnull JsonElement data) {
        this.requireAsset(data, "Shop", this.shopId, null, BuilderDescriptorState.Stable,
                "The barter shop to open from the merchant page", null);
        this.requireInstructionType(EnumSet.of(InstructionType.Interaction));
        return this;
    }

    @Nonnull
    public String getShopId(@Nonnull BuilderSupport support) {
        return this.shopId.get(support.getExecutionContext());
    }
}
