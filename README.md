# 🔊 Sound Control

![image](https://cdn.modrinth.com/data/cached_images/b40d94433dd8411c4369c5ffea91e1e32690bd84.png)

[![CurseForge](https://img.shields.io/badge/CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/advanced-sound-control) [![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/lilaktt/soundcontrol) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/sound_control) [![Fabric](https://img.shields.io/badge/Fabric-DBD8BD?style=for-the-badge&logo=fabric&logoColor=211E26)](https://fabricmc.net/) [![NeoForge](https://img.shields.io/badge/NeoForge-E2522D?style=for-the-badge&logo=neoforge&logoColor=white)](https://neoforged.net/) ![Minecraft Versions](https://img.shields.io/badge/Minecraft-1.21%20to%2026.1.1-2b2d2f?style=for-the-badge&logo=minecraft) 

Take absolute control over your Minecraft audio experience! **Sound Control** is a highly customizable client-side utility mod that allows you to individually adjust the volume, mute, and manage every single sound event in the game—including sounds from other mods!

Whether you want to silence annoying nether portals, make creeper footsteps louder, or completely mute a noisy machine from another mod, Sound Control gives you the tools to create your perfect audio environment.

## ✨ Key Features

* **🔊 3D Spatial Sound Radar:** Don't know the exact name of an annoying sound? Press **`Y`** to toggle the 3D Sound Radar! It dynamically visualizes playing sounds directly in your world, placing their IDs exactly where the sound is coming from in 3D space.
* **▶ In-Menu Sound Testing:** Preview sounds before you edit them. In Advanced or Mods mode, simply click the **`▶`** button next to a sound to hear what it is.
* **⚡ Global Golden Toggles:** Instantly mute or adjust the volume of **ALL** block breaking, block placing, footsteps, hitting, or mob hurt sounds with a single click at the top of the Basic list.
* **⭐ Favorites System:** Mark your most frequently tweaked sounds with a ★ star for instant access. Favorite sounds persist across sessions.
* **🔍 Smart Filtering:** Easily find what you need using the built-in search bar, Category buttons (`Mobs`, `Blocks`, `All`), or filter views (`All Sounds`, `Edited Only`, `Favorites Only`).
* **🌐 Language Support:** Fully translated into **12 languages**, including English, Ukrainian, Spanish, German, French, and more.

## ⚙️ Three Unique Control Modes

Press **`V`** to open the main menu and switch between three powerful viewing modes:
1. **Basic Mode:** Keeps things simple. Changes are applied to groups of sounds (e.g., tweaking `minecraft:zombie` affects all zombie-related sounds). Perfect for quick, general adjustments.
2. **Advanced Mode:** Unlocks ultimate precision. See and adjust every specific sound event in the game individually (e.g., mute `stone.break` but keep `stone.place`).
3. **Mods Mode:** Automatically detects your installed mods (like Create, Farmer's Delight, etc.) and gives you a neat sidebar to manage their custom sounds without cluttering the vanilla list.

## 📥 Installation

1. Install [Fabric Loader](https://fabricmc.net/) or [NeoForge](https://neoforged.net/).
2. Download the appropriate version of **Sound Control** from [Modrinth](https://modrinth.com/mod/sound_control) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/advanced-sound-control).
3. Drop the `.jar` file into your `.minecraft/mods` folder.
4. *(Optional but recommended)* Install ModMenu (for Fabric) to easily access the configuration screen from your mod list.

## ⌨️ Usage

* **`V`** — Open the Sound Control Menu
* **`Y`** — Toggle the 3D Sound Radar

*(Both hotkeys can be changed in the standard Minecraft Controls menu).*

## 📖 Sound Wiki

Can't find the exact sound you're looking for? We've generated a complete, categorized reference of all 1800+ vanilla Minecraft sounds. Check out the [Sound Wiki](https://github.com/lilaktt/soundcontrol/blob/26.1.1/soundcontrol-wiki/Home.md) in our repository to easily find any sound ID!

## ❓ FAQ

<details>
<summary><b>Does this mod need to be installed on the server?</b></summary>
<br>
No. Sound Control is <b>100% client-side</b> — just drop it in your mods folder and you're good to go, even on vanilla servers.
</details>

<details>
<summary><b>Does setting volume above 100% actually make sounds louder?</b></summary>
<br>
Yes! The slider goes up to <b>200%</b> and genuinely increases loudness. The mod raises OpenAL's max gain limit and multiplies the volume at engine level, so cranking a sound to 200% makes it noticeably louder than normal.
</details>

<details>
<summary><b>I set a global toggle to mute all footsteps, but one specific sound still plays. Why?</b></summary>
<br>
Individual sound settings <b>override</b> global toggles. If you previously adjusted a specific sound (e.g., <code>stone.step</code>), that setting takes priority. Reset it with the <b>⟲</b> button to let the global toggle work again.
</details>

<details>
<summary><b>Does "Reset All" delete my favorites?</b></summary>
<br>
No! It only resets volumes and mutes. Your <b>★ starred sounds are kept</b>.
</details>

<details>
<summary><b>Why is the ▶ play button greyed out?</b></summary>
<br>
You're in <b>Basic Mode</b>. Sound previewing only works in <b>Advanced</b> and <b>Mods</b> modes — switch at the bottom-left of the menu.
</details>

<details>
<summary><b>Does Sound Control work with Create / Farmer's Delight / other mods?</b></summary>
<br>
Yes! The mod automatically detects <b>every sound</b> registered by any mod. Switch to <b>Mods Mode</b> and you'll see a sidebar listing all mods that add custom sounds — pick one to manage its audio separately.
</details>

<details>
<summary><b>Will this conflict with other sound mods?</b></summary>
<br>
In most cases, no. Sound Control hooks into Minecraft's sound engine at a low level via Mixin. Standard content mods don't touch the same code, so they work together perfectly. Conflicts are only possible with mods that also modify the core <code>SoundEngine</code> or <code>SoundManager</code> classes.
</details>

<details>
<summary><b>Where is the config file saved?</b></summary>
<br>
At <code>.minecraft/config/soundcontrol.json</code>. It's a clean JSON file you can safely edit by hand or back up. Sounds reset to default are automatically removed to keep the file compact.
</details>

<details>
<summary><b>How does the 3D radar work exactly? / Why aren't muted sounds showing?</b></summary>
<br>
Sound labels float <b>in the world</b> at the exact position where the sound is playing — not as a flat HUD list. The mod projects each sound's 3D coordinates onto your screen, so labels move as you look around, just like nameplates. If a sound's effective volume is 0% (muted), the radar intentionally skips it since there's nothing to locate.
</details>

---

*Thank you for using Sound Control! If you enjoy the mod, consider leaving a star* ⭐
