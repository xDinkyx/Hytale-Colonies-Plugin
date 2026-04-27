package com.hytalecolonies.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Colonist info panel shown as a HUD overlay while the storage bench is open. */
public class ColonistInfoHud extends CustomUIHud {

    public static final String LAYOUT = "hytalecolonies/ColonistInfo.ui";

    private String name    = "";
    private String job     = "";
    private String level   = "";
    private String colony  = "";

    public ColonistInfoHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    public void setData(@Nonnull String name, @Nonnull String job, @Nonnull String level, @Nonnull String colony) {
        this.name   = name;
        this.job    = job;
        this.level  = level;
        this.colony = colony;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append(LAYOUT);
        cmd.set("#ColonistName.Text",  name);
        cmd.set("#ColonistJob.Text",   job);
        cmd.set("#ColonistLevel.Text", level);
        cmd.set("#ColonyId.Text",      colony);
    }
}
