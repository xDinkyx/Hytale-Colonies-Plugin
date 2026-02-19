# HStats Setup Guide

This guide explains how to integrate HStats into your Hytale plugin to track analytics.

## 1. Register Your Mod

1.  Go to [hstats.dev](https://hstats.dev) and create an account or log in.
2.  Navigate to your [dashboard](https://hstats.dev/dashboard).
3.  Enter your mod's name and click "Add Mod".
4.  Copy the generated UUID for your mod.

## 2. Add HStats Class to Your Project

1.  Download or copy the `HStats.java` file from the [HStatsExamplePlugin GitHub repository](https://github.com/hstats-dev/HStatsExamplePlugin/blob/main/src/main/java/com/al3x/HStats.java).
2.  Paste the file into your project's source directory.
3.  Update the package name in `HStats.java` to match your project's package structure.

## 3. Initialize HStats

In your main plugin class, add the following line to your `setup()` method:

```java
@Override
protected void setup() {
  super.setup();
  new HStats("YOUR-MOD-UUID", "1.0.0");
}
```

-   Replace `"YOUR-MOD-UUID"` with the UUID you copied from the HStats dashboard.
-   Change `"1.0.0"` to your mod's current version.

## 4. (Optional) Embed Analytics on Your Page

HStats provides embeddable cards to display your mod's analytics on websites like CurseForge or GitHub.

1.  On your HStats dashboard, find the "Embed Card" section.
2.  Customize the card's appearance.
3.  Copy the generated URL or markdown and paste it into your mod's description page.
