# Wireless Utilities

## Overview

Wireless Utilities is a Minecraft mod for version 1.12.2 that adds wireless energy transfer, item handling, and various utility machines. The mod introduces "positional" and "directional" machines that can interact with blocks and entities at a distance, along with special pearls, augments, and modules that extend functionality. Built using Minecraft Forge modding framework with dependencies on CoFH Core.

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Project Structure
- **Java Mod Architecture**: Standard Minecraft Forge mod structure with source code in `src/main/` and build outputs in `build/`
- **Asset Organization**: Resources split between blockstates, models (block/item), recipes, advancements, and loot tables under `assets/wirelessutils/`
- **Build System**: Gradle-based build with Jenkins CI integration

### Core Machine Types
1. **Directional Machines**: Face a direction and affect blocks/entities in that direction
   - Charger, Condenser, Desublimator, Vaporizer
   - Network integration variants (AE2, Refined Storage)

2. **Positional Machines**: Target specific coordinates via positional cards
   - Same machine types as directional, but coordinate-based targeting

### Item System
- **Pearls**: Special throwable items (Fluxed, Charged, Quenched, Scorched, Void) with unique behaviors
- **Cards**: Positional cards for targeting, area cards for multi-block operations
- **Augments**: Machine upgrades (capacity, range, transfer speed, filters, etc.)
- **Modules**: Vaporizer behavior modules (slaughter, capture, teleport, etc.)

### Block Model System
- Uses Forge's multi-layer rendering for complex block visuals
- Custom `layered_template` model loader for dynamic texture composition
- Blockstates handle machine tiers (0-9 levels) and active states

### Recipe System
- Custom recipe factories for NBT copying (`copy_nbt`, `copy_nbt_shapeless`)
- Conditional recipes based on mod configuration and ore dictionary
- Tiered crafting progression (base → upgraded variants)

### Advancement System
- Custom triggers for mod-specific achievements
- Hierarchical progression tree starting from obtaining Fluxed Pearl

## External Dependencies

### Required Mods
- **CoFH Core**: Core library providing energy systems (RF/Forge Energy) and utilities
- **Minecraft Forge**: Modding framework (targeting 1.12.2)

### Optional Integrations
- **Applied Energistics 2 (AE2)**: Network machines for ME system integration
- **Refined Storage**: Network machines and Infinite Wireless Transmitter block
- **JEI (Just Enough Items)**: Recipe viewing integration

### Build Dependencies
- Gradle build system
- Jenkins CI for automated builds
- Minecraft version manifest for vanilla asset handling