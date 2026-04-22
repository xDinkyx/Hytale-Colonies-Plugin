package com.hytalecolonies.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.utils.PasteToolUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserConfig;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserEventData;
import com.hypixel.hytale.server.core.ui.browser.ServerFileBrowser;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.listeners.ConstructorBuildOrderFilter;


/**
 * Prefab picker for the colony constructor tool. Mirrors the server-prefab section of
 * the vanilla PrefabPage but also stores the resolved file path in
 * {@link ConstructorBuildOrderFilter#pendingPrefabPath} so the paste filter can create
 * a construction order without relying on item metadata.
 */
public class ConstructorPrefabPage extends InteractiveCustomUIPage<FileBrowserEventData>
{

    @Nonnull private final ServerFileBrowser browser;

    public ConstructorPrefabPage(@Nonnull PlayerRef playerRef, @Nonnull BuilderToolsPlugin.BuilderState builderState)
    {
        super(playerRef, CustomPageLifetime.CanDismiss, FileBrowserEventData.CODEC);
        FileBrowserConfig config = FileBrowserConfig.builder()
                                           .listElementId("#FileList")
                                           .searchInputId("#SearchInput")
                                           .enableRootSelector(false)
                                           .enableSearch(true)
                                           .enableDirectoryNav(true)
                                           .allowedExtensions(".prefab.json")
                                           .maxResults(50)
                                           .assetPackMode(true, "Server/Prefabs")
                                           .build();
        this.browser = new ServerFileBrowser(config);
    }

    @Override
    public void
    build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store)
    {
        commandBuilder.append("Pages/PrefabListPage.ui");
        this.browser.buildSearchInput(commandBuilder, eventBuilder);
        buildCurrentPath(commandBuilder);
        this.browser.buildFileList(commandBuilder, eventBuilder);
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FileBrowserEventData data)
    {
        if (data.getSearchQuery() != null || data.isBrowseRequested())
        {
            this.browser.handleEvent(data);
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            buildCurrentPath(commandBuilder);
            this.browser.buildFileList(commandBuilder, eventBuilder);
            this.sendUpdate(commandBuilder, eventBuilder, false);
            return;
        }

        String selectedPath = data.getSearchResult() != null ? data.getSearchResult() : data.getFile();
        if (selectedPath == null) {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] Event with no file/search result -- ignoring.");
            return;
        }

        if (this.browser.handleEvent(FileBrowserEventData.file(selectedPath)))
        {
            // Directory navigation — rebuild the list.
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            buildCurrentPath(commandBuilder);
            this.browser.buildFileList(commandBuilder, eventBuilder);
            this.sendUpdate(commandBuilder, eventBuilder, false);
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
            String cur = this.browser.getAssetPackCurrentPath();
            virtualPath = cur.isEmpty() ? selectedPath : cur + "/" + selectedPath;
        }

        Path file = this.browser.resolveAssetPackPath(virtualPath);
        if (file != null && !Files.isDirectory(file))
        {
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] Prefab selected: %s", file);
            handlePrefabSelection(ref, store, file);
        }
        else
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorPage] Could not resolve virtual path '%s' to a file.", virtualPath);
            this.sendUpdate();
        }
    }

    private void handlePrefabSelection(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Path file)
    {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] Player component null during prefab selection.");
            return;
        }
        if (playerComponent.getGameMode() != GameMode.Creative)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] Player not in Creative mode -- ignoring selection.");
            playerComponent.getPageManager().setPage(ref, store, Page.None);
            return;
        }

        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPage] PlayerRef component null during prefab selection.");
            return;
        }

        playerComponent.getPageManager().setPage(ref, store, Page.None);

        // Clear any previous paste lock so the player can place this new order.
        ConstructorBuildOrderFilter.clearPasteLock(playerRefComponent.getUuid());
        // Store the absolute path now so the paste filter has it immediately.
        String absolutePath = file.toAbsolutePath().normalize().toString();
        ConstructorBuildOrderFilter.pendingPrefabPath.put(playerRefComponent.getUuid(), absolutePath);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructorPage] Armed filter for '%s' with prefab '%s'.",
                playerRefComponent.getUsername(), file.getFileName());

        BlockSelection prefab = PrefabStore.get().getPrefab(file);
        BuilderToolsPlugin.addToQueue(playerComponent,
                                      playerRefComponent,
                                      (r, s, componentAccessor) -> s.load(file.getFileName().toString(), prefab, componentAccessor));
        PasteToolUtil.switchToPasteTool(ref, playerComponent, playerRefComponent, store);
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
            String pack = parts[0];
            String sub = parts.length > 1 ? "/" + parts[1] : "";
            displayPath = "HytaleAssets".equals(pack) ? pack + sub : "Mods/" + pack + sub;
        }
        commandBuilder.set("#CurrentPath.Text", displayPath);
    }
}
