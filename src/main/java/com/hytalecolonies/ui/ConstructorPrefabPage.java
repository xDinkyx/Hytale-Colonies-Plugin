package com.hytalecolonies.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPrefabPreview;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserConfig;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserEventData;
import com.hypixel.hytale.server.core.ui.browser.ServerFileBrowser;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionPlacementSession;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Prefab picker for the colony constructor tool: a live 3D preview panel (via {@code BuilderToolPrefabPreview}) plus a Load button that arms the player's
 * {@link ConstructionPlacementSession} with the chosen prefab. Placement itself is handled by the constructor tool's own Primary/Secondary interactions, not
 * BuilderTools clipboard/paste.
 */
public class ConstructorPrefabPage extends InteractiveCustomUIPage<FileBrowserEventData>
{
    private static final int PREVIEW_TILT       = 23;
    private static final int PREVIEW_SPIN_SPEED = 27;
    private static final int PREVIEW_MAX_SIZE   = 100;
    private static final int DEFAULT_BIOME_TINT = 0x5B9E28;
    private static final int DEFAULT_WATER_TINT = 0x0A3355;

    private static final Message MSG_PREFAB_ARMED = Message.translation("server.items.Tool_Colony_Constructor_PlacePrefab.prefabArmed");

    /** Last virtual browser directory path, keyed by player UUID. Restored when the picker reopens. Purely a UI convenience, not gameplay state. */
    private static final Map<UUID, String> LAST_BROWSER_PATH = new ConcurrentHashMap<>();

    @Nonnull
    private final ServerFileBrowser browser;

    /** File currently shown in the 3D preview panel; confirmed as the construction order prefab when the Load button is pressed. */
    @Nullable
    private Path previewedPath;

    public ConstructorPrefabPage(@Nonnull PlayerRef playerRef)
    {
        super(playerRef, CustomPageLifetime.CanDismiss, FileBrowserEventData.CODEC);
        FileBrowserConfig config     = FileBrowserConfig.builder()
                                               .listElementId("#FileList")
                                               .searchInputId("#SearchInput")
                                               .enableRootSelector(false)
                                               .enableSearch(true)
                                               .enableDirectoryNav(true)
                                               .allowedExtensions(".prefab.json")
                                               .maxResults(50)
                                               .assetPackMode(true, "Server/Prefabs")
                                               .build();
        String            savedPath  = LAST_BROWSER_PATH.get(playerRef.getUuid());
        Path              initialDir = (savedPath != null && !savedPath.isEmpty()) ? Paths.get(savedPath) : null;
        this.browser                 = new ServerFileBrowser(config, null, initialDir);
    }

    @Override
    public void
    build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store)
    {
        commandBuilder.append("Pages/PrefabListPage.ui");
        this.browser.buildSearchInput(commandBuilder, eventBuilder);
        buildCurrentPath(commandBuilder);
        this.browser.buildFileList(commandBuilder, eventBuilder);
        buildLoadButton(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FileBrowserEventData data)
    {
        if (data.getSearchQuery() != null)
        {
            this.browser.handleEvent(data);
            rebuildListing();
            return;
        }

        if (data.isBrowseRequested())
        {
            if (this.previewedPath != null)
                handlePrefabSelection(ref, store, this.previewedPath);
            return;
        }

        String selectedPath = data.getSearchResult() != null ? data.getSearchResult() : data.getFile();
        if (selectedPath == null)
        {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] Event with no file/search result -- ignoring.");
            return;
        }

        if (this.browser.handleEvent(FileBrowserEventData.file(selectedPath)))
        {
            // Directory navigation — rebuild the list.
            rebuildListing();
            return;
        }

        // File selected. Resolve virtual asset-pack path to real filesystem path.
        String virtualPath;
        if (data.getSearchResult() != null)
        {
            virtualPath = selectedPath; // search results already carry the full virtual path
        }
        else
        {
            String cur  = this.browser.getAssetPackCurrentPath();
            virtualPath = cur.isEmpty() ? selectedPath : cur + "/" + selectedPath;
        }

        Path file = this.browser.resolveAssetPackPath(virtualPath);
        if (file != null && !Files.isDirectory(file))
        {
            PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComp != null)
            {
                String curPath = this.browser.getAssetPackCurrentPath();
                if (!curPath.isEmpty())
                    LAST_BROWSER_PATH.put(playerRefComp.getUuid(), curPath);
            }
            handlePreviewRequest(file);
        }
        else
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] Could not resolve virtual path '%s' to a file.", virtualPath);
            this.sendUpdate();
        }
    }

    private void rebuildListing()
    {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder   eventBuilder   = new UIEventBuilder();
        buildCurrentPath(commandBuilder);
        this.browser.buildFileList(commandBuilder, eventBuilder);
        buildLoadButton(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void buildLoadButton(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder)
    {
        commandBuilder.set("#LoadButton.Visible", previewedPath != null);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LoadButton", EventData.of(FileBrowserEventData.KEY_BROWSE, "true"));
    }

    /** Single click on a file row: shows the prefab in the 3D preview panel without arming the construction order yet. */
    private void handlePreviewRequest(@Nonnull Path file)
    {
        if (file.equals(this.previewedPath))
        {
            this.sendUpdate();
            return;
        }

        this.previewedPath = file;
        BlockSelection selection = PrefabStore.get().getPrefab(file);
        sendPreviewPacket(selection);
        rebuildListing();
    }

    private void sendPreviewPacket(@Nullable BlockSelection selection)
    {
        BuilderToolPrefabPreview packet = new BuilderToolPrefabPreview();
        if (selection != null)
        {
            packet.tilt         = PREVIEW_TILT;
            packet.spinSpeed    = PREVIEW_SPIN_SPEED;
            packet.previewScale = PREVIEW_MAX_SIZE;
            if (selection.getBlockCount() > BuilderToolsPlugin.STREAM_TO_CLIENT_LIMIT)
            {
                packet.blocksOmitted = true;
                packet.bounds        = selection.toEditorSelectionBounds();
            }
            else
            {
                var editorPacket     = selection.toPacket();
                packet.blocksChange  = editorPacket.blocksChange;
                packet.fluidsChange  = editorPacket.fluidsChange;
                packet.entityChanges = editorPacket.entityChanges;
            }
            applyTintFromPlayerPosition(packet);
        }
        this.playerRef.getPacketHandler().write(packet);
    }

    /** Mirrors the vanilla prefab browser's tint sampling so the hologram in the preview panel matches the player's local biome/water. */
    private void applyTintFromPlayerPosition(@Nonnull BuilderToolPrefabPreview packet)
    {
        Ref<EntityStore> playerEntityRef = this.playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid())
        {
            packet.biomeTint = DEFAULT_BIOME_TINT;
            packet.waterTint = DEFAULT_WATER_TINT;
            return;
        }

        World world = playerEntityRef.getStore().getExternalData().getWorld();
        var   pos   = this.playerRef.getTransform().getPosition();
        int   x     = MathUtil.floor(pos.x);
        int   y     = MathUtil.floor(pos.y);
        int   z     = MathUtil.floor(pos.z);

        long            chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var             chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef   = chunkStore.getChunkReference(chunkIndex);
        BlockChunk      blockChunk = chunkRef != null && chunkRef.isValid() ? chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType()) : null;
        if (blockChunk == null)
        {
            packet.biomeTint = DEFAULT_BIOME_TINT;
            packet.waterTint = DEFAULT_WATER_TINT;
            return;
        }

        packet.biomeTint = blockChunk.getTint(x, z);

        Ref<ChunkStore>    sectionRef        = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        EnvironmentSection environmentSection =
                sectionRef != null && sectionRef.isValid() ? chunkStore.getStore().getComponent(sectionRef, EnvironmentSection.getComponentType()) : null;
        int envId       = environmentSection != null ? environmentSection.get(x, y, z) : Environment.UNKNOWN_ID;
        var environment = Environment.getAssetMap().getAsset(envId);
        if (environment != null && environment.getWaterTint() != null)
        {
            var waterColor   = environment.getWaterTint();
            packet.waterTint = (waterColor.red & 0xFF) << 16 | (waterColor.green & 0xFF) << 8 | (waterColor.blue & 0xFF);
            return;
        }
        packet.waterTint = DEFAULT_WATER_TINT;
    }

    private void handlePrefabSelection(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Path file)
    {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] Player component null during prefab selection.");
            return;
        }

        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] PlayerRef component null during prefab selection.");
            return;
        }

        playerComponent.getPageManager().setPage(ref, store, Page.None);

        String absolutePath = file.toAbsolutePath().normalize().toString();
        ConstructionPlacementSession session = ConstructionPlacementSession.get(playerRefComponent.getUuid());
        session.prefabId        = absolutePath;
        session.position        = null;
        session.rotationDegrees = 0;
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructorPage] Armed placement session for '%s' with prefab '%s'.",
                      playerRefComponent.getUsername(),
                      file.getFileName());
        playerRefComponent.sendMessage(MSG_PREFAB_ARMED);
    }

    private void buildCurrentPath(@Nonnull UICommandBuilder commandBuilder)
    {
        String cur = this.browser.getAssetPackCurrentPath();
        String displayPath;
        if (cur.isEmpty())
        {
            displayPath = "Assets";
        }
        else
        {
            String[] parts = cur.split("/", 2);
            String   pack  = parts[0];
            String   sub   = parts.length > 1 ? "/" + parts[1] : "";
            displayPath    = "HytaleAssets".equals(pack) ? pack + sub : "Mods/" + pack + sub;
        }
        commandBuilder.set("#CurrentPath.Text", displayPath);
    }
}
