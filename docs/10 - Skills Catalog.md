# Skills Catalog

Adapt ships 23 skill lines. Each one watches something you already do in Minecraft, pays experience for it, and turns that experience into knowledge you spend on adaptations. Adaptations are the actual perks: a few hundred small changes to how the game treats you, from silk-touch glass to a bow that curves its arrows around corners.

Nothing is unlocked by default. You choose which lines to push and which adaptations inside them to buy, so two players on the same server can end up with completely different toolkits. Levels come from play, not from grinding a menu, and the lines that pay best are the ones matching how you already spend your time.

This page is the index. Each line has its own numbered doc, `11` through `33`, in the same order they are listed below, and those docs cover every adaptation in detail. Start here to find the line you want, then go read its doc.

## How any skill works

1. Open the Adapt menu with the activator block, a bookshelf by default, or run `/adapt gui`.
2. Pick a line and go do the thing it tracks. Chop wood for Axes, place blocks for Architect, take hits for TragOul. Experience arrives on its own.
3. Levels pay out knowledge. Open that line's menu and spend knowledge to learn adaptations and raise their levels. Knowledge is the only currency; there is nothing else to buy them with.
4. Use what you bought. Many adaptations are passive and start working the moment you learn them. The rest need a gesture, and the skill doc spells out each one.

Every adaptation also needs its skill and itself enabled in config, the matching `adapt.use` permission, and permission from whatever protection plugin or region policy covers where you are standing.

## The skill lines

### Agility (`agility`)

Movement. Sprint, swim, climb, and stay airborne to level it, then spend it on wall jumps, air dashes, slides, and rolls that turn a lethal fall into a hungry one.

### Architect (`architect`)

Building. Levels from placing blocks, and pays back with builders wands, chalk guides drawn in the air, self-dissolving scaffolds, wool elevators, and an undo for your own fresh mistakes.

### Axes (`axes`)

Chopping and axe combat. Fell a whole tree from the base log, vein-mine wood and leaves, throw your axe as a spinning projectile, and shred armor off whatever you hit.

### Blocking (`blocking`)

Shields. Level by blocking hits, then unlock counters, reflected projectiles, perfect guards that negate a hit outright, and recipes for chainmail, saddles, and horse armor.

### Brewing (`brewing`)

Potions. Level by brewing, then run stands hotter, stretch potion durations, and unlock a long list of bottled effects vanilla never gave you.

### Chronos (`chronos`)

Time. Level by moving, sleeping, and just surviving, faster if you carry a clock. Rewind to a saved moment, freeze projectiles in a stasis bubble, and speed up the furnaces and crops around you.

### Crafting (`crafting`)

The crafting grid. Level by crafting, then salvage items back into components, craft in bulk, carry portable workstations, and stamp your gear with a signature villagers respect.

### Discovery (`discovery`)

Exploring and collecting experience. Pays for seeing things for the first time, reveals structures and hidden chests, and puts names, health bars, and damage numbers on what you are looking at.

### Enchanting (`enchanting`)

The enchanting table, anvil, and grindstone. Cheaper anvil work, rerolled offers you can preview before committing, enchantments recovered onto books, and gear soul-linked so it survives your death.

### Excavation (`excavation`)

Shovels and soft ground. Haste that lasts long enough to finish the block, ore glowing through the dirt, whole planes dug at once, burrows straight down, and treasure in the gravel.

### Herbalism (`herbalism`)

Farming and gathering. Growth auras, harvest-and-replant in one click, composting cascades that mature a whole field, and a hunger bar that soaks damage before your health does.

### Hunter (`hunter`)

Killing mobs. Most of what it gives you triggers when you take a hit, not when you land one, and it pays for those boons in hunger.

### Kinetics (`kinetics`)

Momentum, maces, and spears. Higher jumps, springier landings, meteor dives into a mace smash, shockwaves, and spears that hit harder the faster you charge.

### Nether (`nether`)

Surviving the Nether. Fire and wither resistance, walking on lava, better piglin bartering, faster mining in netherrack, and striders that actually behave.

### Pickaxes (`pickaxe`)

Mining. Vein-mine ore, autosmelt what you break, see ore through stone, get haste in deepslate and obsidian, and carry a pickaxe that refuses to break.

### Ranged (`ranged`)

Bows and crossbows. Farther and faster shots, piercing, ricochets off walls, a dotted trajectory preview, and arrows that lock on and curve to find their mark.

### Rift (`rift`)

Ender items and teleporting. Blink to where you are looking, bind a recall gate, open containers from anywhere, and survive a lethal hit by blinking out of it.

### Seaborne (`seaborne`)

Water. Longer breath, faster swimming, night vision under the surface, trident mastery, ink clouds, and salvage from underwater wrecks.

### Stealth (`stealth`)

Sneaking and striking unseen. Backstab damage while undetected, shadow decoys that pull aggro, smoke pellets, trap sense, and clean assassinations on non-boss mobs.

### Swords (`swords`)

Sword combat. Bleed and poison on your strikes, dual-wield stances, ripostes off a block, sprinting lunges, and extra damage against anything already hurt.

### Taming (`taming`)

Pets. Tougher, faster, harder-hitting companions, a pack that focuses what you mark, mounted combat bonuses, and pets that dive in front of arrows for you.

### TragOul (`tragoul`)

Blood. Level by taking damage and living at low health. Thorns, life siphoned from every hit, corpse explosions, raised skeletal servants, and a killing blow that leaves you at 1 HP instead.

### Unarmed (`unarmed`)

Fists. Combo chains, disarms that knock the weapon out of a player's hand, shockwave claps, grapples that let you throw people, and damage that climbs as your armor drops.

## Reference

### Skill index

| Doc | Skill id | Display name | Adaptations | Icon | Color |
|-----|----------|--------------|-------------|------|-------|
| `11 - Skill - Agility.md` | `agility` | Agility | 13 | `FEATHER` | `GREEN` |
| `12 - Skill - Architect.md` | `architect` | Architect | 12 | `SMITHING_TABLE` | `AQUA` |
| `13 - Skill - Axes.md` | `axes` | Axes | 11 plus 1 Iris-conditional | `GOLDEN_AXE` | `YELLOW` |
| `14 - Skill - Blocking.md` | `blocking` | Blocking | 14 | `SHIELD` | `DARK_GRAY` |
| `15 - Skill - Brewing.md` | `brewing` | Brewing | 13 | `LINGERING_POTION` | `LIGHT_PURPLE` |
| `16 - Skill - Chronos.md` | `chronos` | Chronos | 13 | `CLOCK` | `AQUA` |
| `17 - Skill - Crafting.md` | `crafting` | Crafting | 14 | `CRAFTING_TABLE` | `YELLOW` |
| `18 - Skill - Discovery.md` | `discovery` | Discovery | 14 | `FILLED_MAP` | `AQUA` |
| `19 - Skill - Enchanting.md` | `enchanting` | Enchanting | 14 | `KNOWLEDGE_BOOK` | `LIGHT_PURPLE` |
| `20 - Skill - Excavation.md` | `excavation` | Excavation | 12 | `DIAMOND_SHOVEL` | `YELLOW` |
| `21 - Skill - Herbalism.md` | `herbalism` | Herbalism | 15 | `WHEAT` | `GREEN` |
| `22 - Skill - Hunter.md` | `hunter` | Hunter | 14 | `BONE` | `RED` |
| `23 - Skill - Kinetics.md` | `kinetics` | Kinetics | 18 | `MACE` | `GOLD` |
| `24 - Skill - Nether.md` | `nether` | Nether | 14 | `NETHER_STAR` | `DARK_GRAY` |
| `25 - Skill - Pickaxes.md` | `pickaxe` | Pickaxes | 13 | `NETHERITE_PICKAXE` | `GOLD` |
| `26 - Skill - Ranged.md` | `ranged` | Ranged | 12 | `CROSSBOW` | `DARK_GREEN` |
| `27 - Skill - Rift.md` | `rift` | Rift | 13 | `ENDER_EYE` | `DARK_PURPLE` |
| `28 - Skill - Seaborne.md` | `seaborne` | Seaborne | 14 | `TRIDENT` | `BLUE` |
| `29 - Skill - Stealth.md` | `stealth` | Stealth | 14 | `WITHER_ROSE` | `DARK_GRAY` |
| `30 - Skill - Swords.md` | `swords` | Swords | 14 | `DIAMOND_SWORD` | `YELLOW` |
| `31 - Skill - Taming.md` | `taming` | Taming | 14 | `LEAD` | `GOLD` |
| `32 - Skill - TragOul.md` | `tragoul` | TragOul | 14 | `CRIMSON_ROOTS` | `AQUA` |
| `33 - Skill - Unarmed.md` | `unarmed` | Unarmed | 12 | `FIRE_CHARGE` | `YELLOW` |

Adapt declares 312 adaptation types. 311 are registered on a plain server; the twelfth Axes entry, Iris Feller, only registers when the Iris tree-feller link is available.

`SkillArchitect` sets its icon twice, ending on `SMITHING_TABLE`, so that is what shows in the menu rather than the `IRON_BARS` it sets first.

### Adaptation quick usage index

| Skill | Adaptation id | Name | Usage summary |
|-------|---------------|------|---------------|
| Agility (`agility`) | `agility-wind-up` | Wind Up | Continuous sprinting builds movement speed up to the configured cap; stopping or leaving a valid movement state clears the buildup. |
| Agility (`agility`) | `agility-wall-jump` | Wall Jump | Hold shift while mid-air against a wall to latch, then release shift to jump. |
| Agility (`agility`) | `agility-super-jump` | Super Jump | Sneak and jump to launch a super jump. Four levels scale the apex from 1.5 to 2.5 blocks. |
| Agility (`agility`) | `agility-armor-up` | Armor-Up | Build temporary armor while sprinting; the bonus fades after you stop. |
| Agility (`agility`) | `agility-ladder-slide` | Ladder Slide | Look up to climb quickly and look down to descend quickly. Looking near the horizon returns to normal ladder control, sneaking halts directional movement, and the first and last two climbable blocks always use normal control. |
| Agility (`agility`) | `agility-roll-landing` | Roll Landing | Timed crouch before landing converts part of fall damage into hunger cost. |
| Agility (`agility`) | `agility-slipstream-slide` | Slipstream Slide | Tap sneak while sprinting to enter a sustained prone slide and shed ground friction, keeping momentum through 1-block gaps. |
| Agility (`agility`) | `agility-air-dash` | Air Dash | Left-click air after a sprint-jump to dash forward with pure horizontal velocity. |
| Agility (`agility`) | `agility-cat-reflexes` | Cat Reflexes | A chance while sprinting to dodge incoming projectiles entirely. |
| Agility (`agility`) | `agility-featherfoot` | Featherfoot | Sprinting ignores farmland, pressure plates, sweet-berry snags, and eventually powder snow. |
| Agility (`agility`) | `agility-vault` | Vault | Jump toward a fence to clear it. |
| Agility (`agility`) | `agility-marathoner` | Marathoner | Sprinting drains less saturation, letting you run further before hunger bites. |
| Agility (`agility`) | `agility-kip-up` | Kip-Up | Jump right after taking a hit to convert knockback into recovered momentum and a speed burst. |
| Architect (`architect`) | `architect-glass` | Silk-Touch Glass | Break glass blocks with an empty hand to pick them up without shattering them. |
| Architect (`architect`) | `architect-foundation` | Magic Foundation | Sneak to place a temporary foundation beneath you. |
| Architect (`architect`) | `architect-placement` | Builders Wand | Sneak while holding a block that matches the surface you are looking at to place multiple blocks across it at once. You may need to move slightly to refresh the placement preview. |
| Architect (`architect`) | `architect-wireless-redstone` | Redstone Remote | Use a redstone torch to toggle redstone remotely. |
| Architect (`architect`) | `architect-elevator` | Elevator | Build fast vertical elevators. Craft each Elevator Block with an Ender Pearl in the center, surrounded by 8 Wool. |
| Architect (`architect`) | `architect-smart-shape` | Smart Shape | Sneak-punch blocks with an empty hand to rotate orientation. |
| Architect (`architect`) | `architect-scaffolder` | Scaffolder | Sneak-place blocks as temporary scaffolds that dissolve on their own and refund the block to you. |
| Architect (`architect`) | `architect-supply-line` | Supply Line | When the stack in your hand runs out, it refills automatically from shulker boxes or bundles in your inventory. |
| Architect (`architect`) | `architect-steady-hands` | Steady Hands | While bridging over open air you take no knockback, shrug off falls, and place with a steadier rhythm. |
| Architect (`architect`) | `architect-chalk-line` | Chalk Line | Craft persistent Chalk Wands from one Stick and one String in different orientations. Level 1 unlocks straight lines, level 2 polylines, level 3 circles, and level 4 arcs. Each level immediately reveals its new crafting recipe in the vanilla recipe book. Their private block guides appear only while that wand is held; sneak-click the air to clear its saved plan. |
| Architect (`architect`) | `architect-demolition` | Mason's Eraser | Erase your own recent placements near-instantly without producing drops. |
| Architect (`architect`) | `architect-stonecutter-savant` | Stonecutter Savant | Sneak-punch the air with an empty hand to open a stonecutter wherever you are, as long as you carry a stonecutter. |
| Axes (`axes`) | `axe-ground-smash` | Axe Ground Smash | Jump, then crouch and smash all nearby enemies. |
| Axes (`axes`) | `axe-chop` | Axe Chop | Chop down trees by right clicking the base log. |
| Axes (`axes`) | `axe-drop-to-inventory` | Axe Drop-To-Inventory | Chopped wood drops directly into your inventory. |
| Axes (`axes`) | `axe-leaf-veinminer` | Leaf-miner | Break connected leaves in bulk. |
| Axes (`axes`) | `axe-iris-feller` | Iris Feller | Sneak-break an Iris tree with an axe, then keep sneaking and keep that original axe held while the tree erodes outward. Each successfully eroded log costs hunger; the run halts when sneaking stops, the original axe is no longer held, or hunger cannot fund the next log. Accepted runs start an activation cooldown. |
| Axes (`axes`) | `axe-wood-veinminer` | Wood-miner | Break connected logs and wood in bulk. |
| Axes (`axes`) | `axe-logswap` | Lucy's Log-Swapper | Change the flavor of logs in a Crafting Table. |
| Axes (`axes`) | `axe-throwing-axe` | Throwing Axe | Left-click air with an axe to hurl it as a spinning projectile that deals its melee damage. |
| Axes (`axes`) | `axe-sunder` | Sunder | Axe hits shred a target's armor and armor toughness in stacking layers that fade over time. |
| Axes (`axes`) | `axe-cleave` | Cleave | Melee axe swings cleave extra enemies in a short frontal arc. |
| Axes (`axes`) | `axe-bark-hide` | Bark Hide | Chopping logs layers on short-lived absorption that fades once you stop working. |
| Axes (`axes`) | `axe-shield-splitter` | Shield Splitter | Axe hits on blocking foes disable their shield longer and deal bonus damage. |
| Blocking (`blocking`) | `blocking-multiarmor` | Multi-Armor | Bind an elytra to your chestplate and swap between them on the fly. |
| Blocking (`blocking`) | `blocking-chainarmorer` | Chains of Mephistopheles | Unlocks chainmail armor recipes. |
| Blocking (`blocking`) | `blocking-saddlecrafter` | Craftable Saddle | Craft a Saddle with Leather. |
| Blocking (`blocking`) | `blocking-horsearmorer` | Craftable Horse Armor | Unlocks horse armor recipes. |
| Blocking (`blocking`) | `blocking-counter-guard` | Counter Guard | Each blocked hit builds shield stacks. Your next proc consumes stacks to reflect damage to the attacker. |
| Blocking (`blocking`) | `blocking-bastion-stance` | Bastion Stance | While sneaking and actively blocking with a shield, reduce knockback and incoming projectile pressure. |
| Blocking (`blocking`) | `blocking-mirror-block` | Mirror Block | Blocking with a shield can reflect incoming projectiles with reduced follow-up force. |
| Blocking (`blocking`) | `blocking-bulwark-bash` | Bulwark Bash | Sprint-jump and land a shielded crit to trigger a bash shockwave. |
| Blocking (`blocking`) | `blocking-shield-wall` | Shield Wall | While blocking, allies sheltered behind your shield take reduced projectile damage. |
| Blocking (`blocking`) | `blocking-perfect-guard` | Perfect Guard | Raise your shield the instant before a hit lands to negate it entirely and stagger the attacker. |
| Blocking (`blocking`) | `blocking-tempered-guard` | Tempered Guard | Blocked hits can temper your gear, restoring a sliver of shield and armor durability. |
| Blocking (`blocking`) | `blocking-shieldbearers-resolve` | Shieldbearer's Resolve | When an axe disables your shield, brace with resistance and recover the shield faster. |
| Blocking (`blocking`) | `blocking-phalanx-crafter` | Phalanx Crafter | Craft banner-faced shields directly, and reinforce shields with netherite for bonus durability. |
| Blocking (`blocking`) | `blocking-interpose` | Interpose | Sneak-block near a wounded ally to redirect part of the damage they take onto your shield. |
| Brewing (`brewing`) | `brewing-lingering` | Lingering Brew | Brewed potions last longer. |
| Brewing (`brewing`) | `brewing-super-heated` | Super Heated Brew | Brewing stands work faster the hotter they are. |
| Brewing (`brewing`) | `brewing-absorption` | Bottled Absorption | Unlocks brewing Potions of Absorption for temporary bonus hearts. |
| Brewing (`brewing`) | `brewing-blindness` | Bottled Blindness | Unlocks brewing Potions of Blindness, which shroud a target's sight. |
| Brewing (`brewing`) | `brewing-darkness` | Bottled Darkness | Unlocks brewing Potions of Darkness, which shroud vision and prevent sprinting. |
| Brewing (`brewing`) | `brewing-decay` | Bottled Decay | Unlocks brewing Potions of Wither, which afflict a target with decay. |
| Brewing (`brewing`) | `brewing-fatigue` | Bottled Fatigue | Unlocks brewing Potions of Mining Fatigue, which slow a target's digging and attacks. |
| Brewing (`brewing`) | `brewing-haste` | Bottled Haste | Unlocks brewing Potions of Haste for faster mining, when Efficiency is not enough. |
| Brewing (`brewing`) | `brewing-healthboost` | Bottled Life | Unlocks brewing Potions of Health Boost for extra maximum hearts. |
| Brewing (`brewing`) | `brewing-hunger` | Bottled Hunger | Unlocks brewing Potions of Hunger, which drain a target's food. |
| Brewing (`brewing`) | `brewing-nausea` | Bottled Nausea | Unlocks brewing Potions of Nausea, which warp a target's vision. |
| Brewing (`brewing`) | `brewing-resistance` | Bottled Resistance | Unlocks brewing Potions of Resistance, which reduce incoming damage. |
| Brewing (`brewing`) | `brewing-saturation` | Bottled Saturation | Unlocks brewing Potions of Saturation, which restore hunger. |
| Chronos (`chronos`) | `chronos-time-bottle` | Time In A Bottle | Carry a temporal bottle that stores time and spend it to accelerate timed blocks, growables, and Ageable entities such as baby animals. Its shapeless recipe uses a Swiftness Potion, a Clock, and a Glass Bottle. |
| Chronos (`chronos`) | `chronos-aberrant-touch` | Aberrant Touch | Melee attacks apply stacking slowness at the cost of hunger, with strict PvP caps, and root targets at 5 stacks. |
| Chronos (`chronos`) | `chronos-instant-recall` | Instant Recall | Rewind to a recent snapshot with health and hunger restored. Costs the clock and part of your remaining health, but never kills you. |
| Chronos (`chronos`) | `chronos-time-bomb` | Time Bomb | Throw a crafted chrono bomb that creates a temporal field, slows entities, and freezes projectiles. |
| Chronos (`chronos`) | `chronos-temporal-echo` | Temporal Echo | Projectile actions can replay once after a short delay at reduced strength. |
| Chronos (`chronos`) | `chronos-stasis-field` | Stasis Field | Sneak and right click with an amethyst shard to deploy a stasis bubble that freezes projectiles in midair and locks down mobs inside. Consumes the shard on cast. |
| Chronos (`chronos`) | `chronos-rewind` | Rewind | Sneak and swap hands to mark a moment in time, then do it again within the window to snap back with health and hunger restored. Each rewind costs hunger. |
| Chronos (`chronos`) | `chronos-borrowed-time` | Borrowed Time | A portion of incoming damage is deferred and quietly drained back once per second over the following seconds. |
| Chronos (`chronos`) | `chronos-overtime` | Overtime | Beneficial potion effects applied to you last longer, scaled by adaptation level. At max level, harmful effects applied to you last half as long. |
| Chronos (`chronos`) | `chronos-accelerate` | Accelerate | Passively accelerate time around you, occasionally growing nearby crops and fast-forwarding furnaces, smokers, blast furnaces, and brewing stands. |
| Chronos (`chronos`) | `chronos-hourglass-guard` | Hourglass Guard | A killing blow instead leaves you at half a heart, granting brief invulnerability and slowing nearby enemies, on a long cooldown. |
| Chronos (`chronos`) | `chronos-pocket-watch` | Pocket Watch | Sneak while falling with a clock in your inventory to drift in slow motion for a limited, level scaled duration each airtime. |
| Chronos (`chronos`) | `chronos-deja-vu` | Deja Vu | Your body remembers recent pain; taking the same kind of damage again within a short window hurts noticeably less. |
| Crafting (`crafting`) | `crafting-deconstruction` | Deconstruction | Deconstruct blocks & items into salvageable base components. |
| Crafting (`crafting`) | `crafting-xp` | Crafting XP | Gain passive XP when crafting. |
| Crafting (`crafting`) | `crafting-leather` | Craftable Leather | Craft Leather from Rotten Flesh. |
| Crafting (`crafting`) | `crafting-skulls` | Craftable Skulls | Unlocks recipes for mob skulls. |
| Crafting (`crafting`) | `crafting-backpacks` | Backpacks | Craft a Backpack that stores whole stacks and opens as its own container. |
| Crafting (`crafting`) | `crafting-stations` | Portable Tables | Click the air while holding an anvil, crafting table, grindstone, stonecutter, cartography table, or loom to open it without placing it. Each open costs hunger. |
| Crafting (`crafting`) | `crafting-reconstruction` | Ore Reconstruction | Recraft ores from their base components. |
| Crafting (`crafting`) | `crafting-bulk-artisan` | Bulk Artisan | Shift-click a craft result to pull extra ingredients from your inventory and craft a bigger batch at once. |
| Crafting (`crafting`) | `crafting-thrifty-hands` | Thrifty Hands | Every craft has a chance to refund one of its ingredients. |
| Crafting (`crafting`) | `crafting-masterwork` | Masterwork | Tools and armor you craft can roll bonus durability, with a chance for a minor attribute bonus at full level. |
| Crafting (`crafting`) | `crafting-compactor` | Compactor | Sneak and swap hands while aiming at a Crafting Table to compact full stacks into blocks immediately. |
| Crafting (`crafting`) | `crafting-tinkerer` | Tinkerer | Combine two damaged tools of the same type in the crafting grid to keep their best enchantments. |
| Crafting (`crafting`) | `crafting-provisioner` | Provisioner | Crafting or cooking food has a chance to yield bonus portions. |
| Crafting (`crafting`) | `crafting-signature` | Artisan's Signature | Items you craft carry your signature, and villagers offer better trades while you carry your signed goods. |
| Discovery (`discovery`) | `discovery-unity` | Experimental Unity | Collecting Experience Orbs adds XP to random skills. |
| Discovery (`discovery`) | `discovery-world-armor` | World Armor | Passive armor depending on nearby block hardness. |
| Discovery (`discovery`) | `discovery-xp-resist` | Experimental Resistance | Consume experience to mitigate damage only when a hit would drop you below 5 hearts or kill you. |
| Discovery (`discovery`) | `discovery-villager-att` | Villager Attraction | Improves villager trades at the cost of XP per interaction. |
| Discovery (`discovery`) | `discovery-better-mending` | Better Mending | Sneak-left-click to spend your stored XP and directly mend the Mending item in your hand. |
| Discovery (`discovery`) | `discovery-archaeologist` | Archaeologist | Brushing suspicious blocks can yield bonus archaeology rewards. |
| Discovery (`discovery`) | `discovery-cartographer-pulse` | Cartographer Pulse | Sneak-right-click with a compass to lock it toward a nearby structure and see a private glowing direction line. Each pulse costs hunger. |
| Discovery (`discovery`) | `discovery-insight` | Insight | Study creatures at a glance: the entity you look at shows its name and health bar above its head, tameable creatures show their live speed, jump, and attack stats, and your hits show floating damage numbers with crits in orange. |
| Discovery (`discovery`) | `discovery-trailblazer` | Trailblazer | Your first visit to each biome or structure type grants a skill-XP burst and brief speed. |
| Discovery (`discovery`) | `discovery-field-notes` | Field Notes | Your first kill of each mob species pays big XP and banks a small permanent damage bonus against that species. |
| Discovery (`discovery`) | `discovery-polymath` | Polymath | Each skill you have leveled past a threshold adds a small global XP-gain bonus. |
| Discovery (`discovery`) | `discovery-relic-appraiser` | Relic Appraiser | Sneak-right-click rare drops (heads, discs, armor trims, pottery sherds) to appraise them for Discovery XP; appraised items gain a lore tag. |
| Discovery (`discovery`) | `discovery-sixth-sense` | Sixth Sense | A private glowing direction line hints when an unexplored structure is within range. |
| Discovery (`discovery`) | `discovery-keen-eye` | Keen Eye | Chests and spawners in your line of sight briefly appear as private glowing block displays. |
| Enchanting (`enchanting`) | `enchanting-quick-enchant` | Quick-Click Enchant | Enchant items by clicking enchant books directly on them. |
| Enchanting (`enchanting`) | `enchanting-lapis-return` | Lapis Return | Enchanting at a table has a chance to refund lapis, more at higher levels. |
| Enchanting (`enchanting`) | `enchanting-xp-return` | XP Return | Enchanting XP is returned to you when you enchant an item. |
| Enchanting (`enchanting`) | `enchanting-anvil-savant` | Anvil Savant | Reduce anvil XP cost when combining, repairing, and renaming. |
| Enchanting (`enchanting`) | `enchanting-offer-reroll` | Offer Reroll | Sneak-right-click an enchanting table to reroll its offers. Each reroll costs lapis and XP levels. |
| Enchanting (`enchanting`) | `enchanting-bookshelf-attunement` | Bookshelf Attunement | Gain virtual bookshelf power to improve enchanting table offer quality. |
| Enchanting (`enchanting`) | `enchanting-grindstone-recovery` | Grindstone Recovery | Disenchanting can recover one removed enchantment onto a book with bonus XP. |
| Enchanting (`enchanting`) | `enchanting-curse-cleansing` | Curse Cleansing | Sneak while taking a grindstone result to remove curses from the original item first, preserve every other property, and gain Enchanting XP. |
| Enchanting (`enchanting`) | `enchanting-tome-rebinding` | Tome Rebinding | Sneak-right-click a multi-enchant book in an anvil to split it into single-enchant books. Lossy at low levels, lossless at max. |
| Enchanting (`enchanting`) | `enchanting-soul-link` | Soul Link | Sneak-right-click an anvil to soul-link an enchanted item so it survives death, gated by an XP level buffer. |
| Enchanting (`enchanting`) | `enchanting-arcane-siphon` | Arcane Siphon | Killing mobs in enchanted gear grants bonus XP and can siphon a book of their enchantments. |
| Enchanting (`enchanting`) | `enchanting-rune-sight` | Rune Sight | Reveal the enchantments behind enchanting-table offers before you commit. One at first, the full list at max. |
| Enchanting (`enchanting`) | `enchanting-infusion-transfer` | Infusion Transfer | Right-click the base item in an anvil to move one enchantment onto it from the sacrifice item. |
| Enchanting (`enchanting`) | `enchanting-echo-of-knowledge` | Echo of Knowledge | Hold an enchanted book while collecting XP to charge it and upgrade an enchantment within vanilla caps. |
| Excavation (`excavation`) | `excavation-haste` | Hasty Excavator | Starting a block break grants stable Haste long enough to finish slower excavation blocks. |
| Excavation (`excavation`) | `excavation-spelunker` | Super-Seeing Spelunker | Hold glow berries in your main hand to reveal glowing ore displays through the ground. |
| Excavation (`excavation`) | `excavation-omnitool` | Omnitool | Merge your tools into one omni-tool that swaps to the right tool for the job. Shift-click one tool onto another in your inventory to merge; sneak-drop to disassemble. |
| Excavation (`excavation`) | `excavation-drop-to-inventory` | Shovel Drop-To-Inventory | Excavated blocks drop directly into your inventory. |
| Excavation (`excavation`) | `excavation-seismic-ping` | Seismic Ping | Mining can temporarily reveal a nearby ore block with a private colored glow. |
| Excavation (`excavation`) | `excavation-tunneler` | Tunneler | Sneak while digging soft blocks to carve a whole plane at once. |
| Excavation (`excavation`) | `excavation-treasure-hunter` | Treasure Hunter | Digging sand, gravel, mud, or clay can unearth archaeology treasure. |
| Excavation (`excavation`) | `excavation-soft-fall` | Soft Fall | Landing on soft diggable ground reduces fall damage, up to full negation. |
| Excavation (`excavation`) | `excavation-earth-mover` | Earth Mover | Sneak-right-click the air with a shovel to damage, knock back, and slow hostile mobs with a wave of earth. Damage scales from the held shovel. Each wave costs hunger. |
| Excavation (`excavation`) | `excavation-burrow` | Burrow | Sneak-right-click soft ground with a shovel to rapidly dig straight down, stopping before hazards. Each burrow costs hunger and tool durability. |
| Excavation (`excavation`) | `excavation-grave-digger` | Grave Digger | Digging earthen ground can unearth bone loot, and rarely disturbs a hostile grave. |
| Excavation (`excavation`) | `excavation-mudlark` | Mudlark | Bonus drops from muddy blocks, plus haste while digging in water or rain. |
| Herbalism (`herbalism`) | `herbalism-growth-aura` | Growth Aura | Periodically grows nearby plants. |
| Herbalism (`herbalism`) | `herbalism-replant` | Harvest & Replant | Right click a crop with a hoe to harvest & replant it. |
| Herbalism (`herbalism`) | `herbalism-hungry-shield` | Hungry Shield | Take damage to your hunger before your health, covering more damage types as it levels up. |
| Herbalism (`herbalism`) | `herbalism-hippo` | Herbalist's Hippo | Food restores additional saturation. |
| Herbalism (`herbalism`) | `herbalism-drop-to-inventory` | Hoe Drop-To-Inventory | Harvested crops drop directly into your inventory. |
| Herbalism (`herbalism`) | `herbalism-luck` | Herbalist's Luck | Breaking grass or flowers can add a random item to the drops. |
| Herbalism (`herbalism`) | `herbalism-myconid` | Herbalist's Myconid | Unlocks a mycelium recipe. |
| Herbalism (`herbalism`) | `herbalism-terralid` | Herbalist's Terralid | Unlocks a grass-block recipe. |
| Herbalism (`herbalism`) | `herbalism-mushroom-blocks` | Mushroom Maker | Unlocks mushroom-block recipes. |
| Herbalism (`herbalism`) | `herbalism-cobweb` | Webby Creator | Unlocks a cobweb recipe. |
| Herbalism (`herbalism`) | `herbalism-seed-sower` | Seed Sower | Sneak-right-click with seeds to plant nearby farmland and soul-sand plots. |
| Herbalism (`herbalism`) | `herbalism-compost-cascade` | Compost Cascade | Sneak-right-click a composter to consume nearby drops, harvest and replant mature crops, compost your inventory, and spend the compost maturing nearby crops. Leaves are only consumed when enabled in the config. |
| Herbalism (`herbalism`) | `herbalism-rooted-footing` | Rooted Footing | Permanent passive: protect farmland and convert part of fall damage into hunger while on natural ground. |
| Herbalism (`herbalism`) | `herbalism-bee-shepherd` | Bee Shepherd | Hold flowers near crops to pulse growth and draw nearby bees toward you. |
| Herbalism (`herbalism`) | `herbalism-spore-bloom` | Spore Bloom | Sneak-right-click mycelium with mushrooms to spread controlled bloom patches. |
| Hunter (`hunter`) | `hunter-adrenaline` | Adrenaline | Melee damage increases as health falls. |
| Hunter (`hunter`) | `hunter-regen` | Hunter's Regen | When you are struck you gain regeneration, at the cost of hunger. |
| Hunter (`hunter`) | `hunter-invis` | Vanishing Step | When you are struck you gain invisibility, at the cost of hunger. |
| Hunter (`hunter`) | `hunter-jumpboost` | Hunter's Heights | When you are struck you gain jump-boost, at the cost of hunger. |
| Hunter (`hunter`) | `hunter-luck` | Hunter's Luck | When you are struck you gain luck, at the cost of hunger. |
| Hunter (`hunter`) | `hunter-speed` | Hunter's Speed | When you are struck you gain speed, at the cost of hunger. |
| Hunter (`hunter`) | `hunter-strength` | Hunter's Strength | When you are struck you gain strength, at the cost of hunger. |
| Hunter (`hunter`) | `hunter-resistance` | Hunter's Resistance | When you are struck you gain resistance, at the cost of hunger. |
| Hunter (`hunter`) | `hunter-drop-to-inventory` | Items Drop-To-Inventory | Kills and blocks broken with a sword in hand send their drops straight into your inventory. |
| Hunter (`hunter`) | `hunter-trophy-skinner` | Trophy Skinner | Precision kills can yield bonus trophy drops and occasional mob heads. |
| Hunter (`hunter`) | `hunter-predator-focus` | Predator Focus | Repeated strikes on the same target ramp your damage; switching targets resets the ramp. |
| Hunter (`hunter`) | `hunter-big-game` | Big Game Hunter | Deal bonus damage to and reap extra drops from large and boss-class mobs. |
| Hunter (`hunter`) | `hunter-blood-trail` | Blood Trail | Mobs you wound below half health leave a private glowing blood trail only you can see. |
| Hunter (`hunter`) | `hunter-snare-line` | Snare Line | Craft a string-and-iron snare; hostile mobs that cross it are rooted briefly. |
| Kinetics (`kinetics`) | `kinetics-moon-jump` | Moon Jump | Each level raises every jump by 0.5 blocks. Sneak-jump for an additional floaty, low-gravity hop. |
| Kinetics (`kinetics`) | `kinetics-rubber-soul` | Rubber Soul | Your landings carry spring. Bouncy blocks send you higher, and every landing keeps more momentum. |
| Kinetics (`kinetics`) | `kinetics-soft-catch` | Soft Catch | Soft and springy blocks break your fall, and a fresh bounce grants a grace window. |
| Kinetics (`kinetics`) | `kinetics-surface-skate` | Surface Skate | Sprint to slide across the ground with lowered friction; sneak to grip hard. |
| Kinetics (`kinetics`) | `kinetics-terminal-toggle` | Terminal Toggle | Sneak in midair to switch between a hard dive and a drifting hang. |
| Kinetics (`kinetics`) | `kinetics-heavy-frame` | Heavy Frame | Plant your feet while sneaking with a mace or spear: heavy knockback resistance at the cost of speed. |
| Kinetics (`kinetics`) | `kinetics-mass-shift` | Mass Shift | Sneak and swap hands: look up for persistent Titan form, down for persistent Pocket form, or level to return to normal. Titan grants 20% damage and health with Slowness I; Pocket trades 20% damage and health for Speed I. |
| Kinetics (`kinetics`) | `kinetics-meteor-cadence` | Meteor Cadence | Sneak while falling with a mace to accelerate sharply downward into your smash. |
| Kinetics (`kinetics`) | `kinetics-breachwright` | Breachwright | Your mace smashes shred the target's armor for a short time. |
| Kinetics (`kinetics`) | `kinetics-windburst` | Windburst | Heavy smashes erupt in a radial shockwave that hurls nearby enemies away. |
| Kinetics (`kinetics`) | `kinetics-quake-guard` | Quake Guard | Landing a smash braces you: knockback resistance, toughness, and safe footing for a moment. |
| Kinetics (`kinetics`) | `kinetics-rebound-anvil` | Rebound Anvil | Each smash coils your legs: land springy and cushioned, ready for a second meteor. |
| Kinetics (`kinetics`) | `kinetics-phalanx-reach` | Phalanx Reach | Spears strike farther in your hands. |
| Kinetics (`kinetics`) | `kinetics-charge-lance` | Charge Lance | Spear hits scale with your speed. Hit them at a run. |
| Kinetics (`kinetics`) | `kinetics-impale-pin` | Impale Pin | Spear hits at sweet range pin the target with heavy slowness. |
| Kinetics (`kinetics`) | `kinetics-lunge-conductor` | Lunge Conductor | Your spear lunges strike harder and carry you farther. |
| Kinetics (`kinetics`) | `kinetics-mounted-shock` | Mounted Shock | Spear charges from the saddle hit harder the faster your mount moves. |
| Kinetics (`kinetics`) | `kinetics-dead-zone` | Dead Zone | Enemies that crowd inside your spear's dead zone get shoved out, arming a riposte. |
| Nether (`nether`) | `nether-wither-resist` | Wither Resistance | Resists withering through the power of Netherite. |
| Nether (`nether`) | `nether-skull-toss` | Wither Skull Throw | Use a player head to activate a temporary Wither form. |
| Nether (`nether`) | `nether-fire-resist` | Fire Resistance | Resists fire by hardening your skin. |
| Nether (`nether`) | `nether-lava-walker` | Lava Walker | Stride over lava in the Nether at the cost of hunger. |
| Nether (`nether`) | `nether-ghast-ward` | Ghast Ward | Harden against ghast blasts and wither-skeleton ranged pressure in the Nether. |
| Nether (`nether`) | `nether-blaze-leech` | Blaze Leech | Fire interactions can trigger brief hunger and regeneration gains. |
| Nether (`nether`) | `nether-piglin-broker` | Piglin Broker | Nearby barters can grant extra rolls and occasional premium bonus items. |
| Nether (`nether`) | `nether-soul-strider` | Soul Strider | Move at full speed across soul sand and soul soil, gaining soul-speed bursts at mastery. |
| Nether (`nether`) | `nether-magma-skin` | Magma Skin | While burning, melee attackers catch fire and your own strikes deal bonus fire damage. |
| Nether (`nether`) | `nether-netherrack-mason` | Netherrack Mason | Mine netherrack, basalt, and blackstone faster in the Nether with occasional bonus drops. |
| Nether (`nether`) | `nether-strider-bond` | Strider Bond | Ride striders faster, keep their pace out of lava, and land safely when dismounting over lava. |
| Nether (`nether`) | `nether-crimson-feast` | Crimson Feast | Eat nether fungi and warped flora, and gain fire resistance from any meal in the Nether. |
| Nether (`nether`) | `nether-ashwalker` | Ashwalker | Ignore magma-block and campfire burns, and shrug off most soul-fire damage at mastery. |
| Nether (`nether`) | `nether-wither-harvest` | Wither Harvest | Wither skeletons yield extra bones and coal, with slightly improved skull odds. |
| Pickaxes (`pickaxe`) | `pickaxe-chisel` | Ore Chisel | Right Click Ores to Chisel more ore out of them, at a severe durability cost. |
| Pickaxes (`pickaxe`) | `pickaxe-veinminer` | Veinminer | Break connected vanilla ore veins and clusters. |
| Pickaxes (`pickaxe`) | `pickaxe-autosmelt` | Autosmelt | Automatically smelts supported vanilla ores when mined. |
| Pickaxes (`pickaxe`) | `pickaxe-drop-to-inventory` | Pickaxe Drop-To-Inventory | Blocks you break send their drops straight into your inventory. |
| Pickaxes (`pickaxe`) | `pickaxe-silk-spawner` | Pickaxe Silk-Spawner | Allows spawners to drop when broken under the documented conditions. |
| Pickaxes (`pickaxe`) | `pickaxe-quarry-sense` | Quarry Sense | Sneak-right-click a block with an iron+ pickaxe to reveal nearby ores as private glowing block displays. |
| Pickaxes (`pickaxe`) | `pickaxe-tunnel-bore` | Tunnel Bore | Sneak and mine stone-type blocks to bore out a whole tunnel face at once. |
| Pickaxes (`pickaxe`) | `pickaxe-deep-core` | Deep Core | Mining deepslate grants Haste so it digs like normal stone. |
| Pickaxes (`pickaxe`) | `pickaxe-obsidian-rush` | Obsidian Rush | Mining obsidian with a diamond or netherite pickaxe grants a strong Haste burst. |
| Pickaxes (`pickaxe`) | `pickaxe-unbreakable-pact` | Unbreakable Pact | Your pickaxe refuses to break, surviving at 1 durability instead. |
| Pickaxes (`pickaxe`) | `pickaxe-repair-rhythm` | Repair Rhythm | Sustained mining has a chance to restore durability to your pickaxe. |
| Pickaxes (`pickaxe`) | `pickaxe-gem-polish` | Gem Polish | Mining gem ores grants bonus XP orbs and a chance for an extra gem. |
| Pickaxes (`pickaxe`) | `pickaxe-stone-skin` | Stone Skin | Breaking stone-type blocks builds short-lived stacking damage resistance. |
| Ranged (`ranged`) | `ranged-force` | Force Shot | Shoot projectiles further, faster. |
| Ranged (`ranged`) | `ranged-piercing` | Arrow Piercing | Adds piercing so projectiles can pass through targets. |
| Ranged (`ranged`) | `ranged-recovery` | Arrow Recovery | Recovers arrows after a projectile kill. |
| Ranged (`ranged`) | `ranged-lunge-shot` | Lunge Shot | While airborne, firing arrows kicks you backward, away from your aim. |
| Ranged (`ranged`) | `ranged-webshot` | Web Snare | Thrown web shots surround the hit target with cobwebs. |
| Ranged (`ranged`) | `ranged-trajectory-sight` | Trajectory Sight | Sneak or draw a ranged weapon to preview projectile flight as a clear dotted line with a ringed impact marker. With a Heartseeker lock active, the preview shows the arrow's curved seeking path to the mark instead. |
| Ranged (`ranged`) | `ranged-floaters` | Floaters | Projectiles have a chance to apply levitation and hold targets in the air. |
| Ranged (`ranged`) | `ranged-pinning-shot` | Pinning Shot | Any player-fired projectile can pin targets with heavy slowness. |
| Ranged (`ranged`) | `ranged-ricochet-bolt` | Ricochet Bolt | Projectiles ricochet off solid blocks with chained bounces. |
| Ranged (`ranged`) | `ranged-fetch-shot` | Fetch Shot | Shoot dropped items with projectiles to pull them straight into your inventory. |
| Ranged (`ranged`) | `ranged-heavy-draw` | Heavy Draw | Heavier projectiles fly slower but hit far harder. |
| Ranged (`ranged`) | `ranged-heartseeker` | Heartseeker | Draw a bow while looking at a creature to lock on - it glows red for you, and your arrow whistles and curves through the air to find it no matter where you aim, weaving around obstacles. Piercing levels and Ricochet Bolt's available bounce capacity add seeking passes: the arrow punches through its target, exits the far side, then bends toward a fresh nearby target; without a new target it keeps flying forward. Ricochet passes preserve their reflection, speed, damage, and rewards when seeking arrows strike blocks. Every seeking shot puts your bow on a short cooldown. |
| Rift (`rift`) | `rift-resist` | Rift Resistance | Gain Resistance when using Ender Items & Abilities. |
| Rift (`rift`) | `rift-access` | Remote Access | Craft a Reliquary Portkey (ender pearl + compass), bind it to a container, and use it to open that container from anywhere. |
| Rift (`rift`) | `rift-enderchest` | Easy Enderchest | Click while holding an ender chest to open your ender chest without placing it. |
| Rift (`rift`) | `rift-gate` | Rift Gate | Craft a recall gate (Emerald + Amethyst Shard + Ender Pearl), then sneak-left-click it to bind your location and right-click to teleport back after a 5 second channel. Sneak-left-click into the air to unbind. |
| Rift (`rift`) | `rift-blink` | Rift Blink | Double-jump to blink toward where you are looking. Aim at ground to land there, at a ledge to mantle onto it, or into open air to dash. Successful blinks consume no pearl, but deal normal ender pearl damage that decreases by level. Sneak while blinking to phase straight through walls and obstacles. |
| Rift (`rift`) | `rift-descent` | Anti-Levitation | Tap sneak while levitating to cancel Levitation and drift down gently with Slow Falling. |
| Rift (`rift`) | `rift-visage` | Rift Visage | Prevents Endermen from becoming aggressive if you have Ender Pearls in your inventory. |
| Rift (`rift`) | `rift-ender-taglock` | Ender Taglock | Sneak-left-click entities with an ender pearl to bind them, then throw the tagged pearl to relocate only that target. The thrower is never teleported. |
| Rift (`rift`) | `rift-inflated-pocket-dimension` | Inflated Pocket Dimension | Empty-hand right-click a block to pull matching stacks from your ender chest; sneak-drop to store items into it. |
| Rift (`rift`) | `rift-void-magnet` | Void Magnet | Sneak to pull nearby item drops into your ender chest first, then inventory overflow. |
| Rift (`rift`) | `rift-void-skin` | Void Skin | Any lethal damage blinks you to a nearby safe spot instead of killing you, using the current world's spawn when no nearby spot exists, and grants brief resistance. Costs an ender pearl and has a long cooldown. Leveling shortens the cooldown and extends the protection. |
| Rift (`rift`) | `rift-pearl-rebound` | Pearl Rebound | A plain thrown ender pearl bounces once instead of teleporting at the first surface it hits. The rebound steers toward your crosshair, then teleports normally at its next impact. Pearl landing damage is also reduced; leveling improves both reduction and steering. |
| Rift (`rift`) | `rift-conduit` | Rift Conduit | Sneak-right-click a container with an ender pearl to capture a conduit taglock, then right-click a second container to link them. Items left in one linked container flow into the other when you close it. At max level the two containers can even be in different dimensions. |
| Seaborne (`seaborne`) | `seaborne-oxygen` | Organic Oxygen Tank | Increases underwater air capacity. |
| Seaborne (`seaborne`) | `seaborne-speed` | Dolphin's Grace | Gain passive water speed; sprint-swimming also applies Dolphin's Grace for a level-scaled duration. |
| Seaborne (`seaborne`) | `seaborne-fishers-fantasy` | Fisher's Fantasy | Fishing can grant additional XP and fish. |
| Seaborne (`seaborne`) | `seaborne-turtles-vision` | Turtle's Vision | While underwater, you gain Night Vision. |
| Seaborne (`seaborne`) | `seaborne-turtles-mining-speed` | Turtle Miner | Gain Haste III while mining underwater after Water Breathing expires; the effect stacks with Aqua Affinity. |
| Seaborne (`seaborne`) | `seaborne-tidecaller` | Tidecaller | Surge forward with a water burst while in water or rain, triggered by sneaking or an attack swing depending on server settings. |
| Seaborne (`seaborne`) | `seaborne-pressure-diver` | Pressure Diver | Gain depth-based protection underwater and partially suppress mining fatigue pressure. |
| Seaborne (`seaborne`) | `seaborne-coral-gardener` | Coral Gardener | Coral you place survives out of water far longer, bonemeal grows coral, and reef blocks grant bonus XP. |
| Seaborne (`seaborne`) | `seaborne-deep-salvager` | Deep Salvager | Underwater containers appear as private aqua glowing block displays and reward bonus treasure the first time you open them submerged. |
| Seaborne (`seaborne`) | `seaborne-ink-veil` | Ink Veil | Taking damage underwater bursts an ink cloud that blinds hostiles and briefly hides you from drowned and guardians. |
| Seaborne (`seaborne`) | `seaborne-trident-mastery` | Trident Mastery | Tridents deal bonus damage and home back to you faster after a throw. |
| Seaborne (`seaborne`) | `seaborne-fish-whisperer` | Fish Whisperer | Fish school toward you, dolphins and axolotls assist your hunts, and you fish with a permanent Luck of the Sea tier. |
| Seaborne (`seaborne`) | `seaborne-hydro-jet` | Hydro Jet | Tap sneak while swimming to burst forward on a jet of water. Costs hunger and consumes a charge. |
| Seaborne (`seaborne`) | `seaborne-brine-skin` | Brine Skin | While wet you slowly regenerate and take reduced damage, and the buff lingers briefly after you dry off. |
| Stealth (`stealth`) | `stealth-silent-step` | Stealth | Sneaking starts a concealment session that evaluates nearby observers. Attacks made while undetected deal increased backstab damage; invisibility, an active Shadow Decoy, or a Smoke Pellet concealment lease also count as undetected. |
| Stealth (`stealth`) | `stealth-speed` | Sneak Speed | Sneak faster with each level - max level sneaks at full walk speed, the vanilla sneak-speed cap. |
| Stealth (`stealth`) | `stealth-snatch` | Item Snatch | Snatch Dropped items instantly while sneaking. |
| Stealth (`stealth`) | `stealth-ghost-armor` | Ghost's Armor | Slowly builds a separate armor layer while you avoid damage. It stacks beyond worn armor and is consumed by the next armor-respecting hit. |
| Stealth (`stealth`) | `stealth-vision` | Stealth Vision | While sneaking, gain night vision, ignore Blindness, and see invisible players outlined. |
| Stealth (`stealth`) | `stealth-enderveil` | Enderveil | Look at Endermen freely - prevents Enderman aggression without wearing a pumpkin. |
| Stealth (`stealth`) | `stealth-shadow-decoy` | Shadow Decoy | Stopping a sneak creates a short-lived decoy with the player's skin and equipment. Nearby mobs redirect their aggression to the decoy while the owner becomes temporarily invisible and leaves a configurable smoke trail. |
| Stealth (`stealth`) | `stealth-shadowmeld` | Shadowmeld | Remain sneaking while Stealth reports you undetected to become invisible. Detection, acting, taking damage, or standing ends the meld. |
| Stealth (`stealth`) | `stealth-smoke-pellet` | Smoke Pellet | Sneaking with gunpowder in either hand consumes one gunpowder and casts a smoke cloud along the player's aim. Players in the cloud become invisible, living entities are blinded, and affected mobs drop their targets and cannot immediately reacquire concealed players. |
| Stealth (`stealth`) | `stealth-cutpurse` | Cutpurse | While Stealth reports you undetected, hits on pillagers, vindicators, and piglins can steal loot without a kill. |
| Stealth (`stealth`) | `stealth-trap-sense` | Trap Sense | While sneaking, nearby trapped chests, tripwire string, hooks, pressure plates, and sculk blocks privately glow. Maximum level prevents all of your movement vibrations from triggering sculk. |
| Stealth (`stealth`) | `stealth-assassinate` | Assassinate | While Stealth reports you undetected, strike eligible non-boss mobs for exactly their current health instead of synthetic overkill damage. |
| Stealth (`stealth`) | `stealth-decoy-swap` | Decoy Swap | Requires Shadow Decoy. While your decoy is alive, double-tap sneak to swap places with it. |
| Stealth (`stealth`) | `stealth-umbral-recovery` | Umbral Recovery | Kills made while sneaking refund hunger and extend any active invisibility window. |
| Swords (`swords`) | `sword-machete` | Machete | Cut through foliage with ease. |
| Swords (`swords`) | `sword-poison-blade` | Poisoned Blade | Strikes with your sword, cause Poison. |
| Swords (`swords`) | `sword-bloody-blade` | Bloody Blade | Strikes with your sword, cause Bleeding. |
| Swords (`swords`) | `sword-dual-wield` | Dual Wield Stance | Holding a sword in each hand grants bonus melee damage. Matching swords grant the higher bonus. |
| Swords (`swords`) | `sword-executioners-edge` | Executioner's Edge | Sword strikes deal extra damage to low-health targets. |
| Swords (`swords`) | `sword-riposte-window` | Riposte Window | Blocking with a shield arms a short riposte for your next strike. |
| Swords (`swords`) | `sword-crimson-cyclone` | Crimson Cyclone | Land a sword crit to set off a bleeding area slash around your target. |
| Swords (`swords`) | `sword-lunge-strike` | Lunge Strike | Sprint-attack with a sword to lunge into the blow with extra reach. |
| Swords (`swords`) | `sword-blade-flow` | Blade Flow | Chain sword hits to build attack-speed stacks. Taking damage breaks the flow. |
| Swords (`swords`) | `sword-duelists-focus` | Duelist's Focus | Deal more damage and take less while exactly one hostile is engaged with you; the focused attacker briefly glows when the defense activates. |
| Swords (`swords`) | `sword-whetstone-ritual` | Whetstone Ritual | Sneak right-click a grindstone with a sword to grind a temporary sharpness buff for durability and XP levels. |
| Swords (`swords`) | `sword-crescent-guard` | Crescent Guard | Killing blows with a sword grant a brief burst of absorption hearts. |
| Swords (`swords`) | `sword-hamstring` | Hamstring | Strikes on sprinting or fleeing targets slow them and stop them sprinting. |
| Swords (`swords`) | `sword-heirloom-edge` | Heirloom Edge | Name a sword at an anvil to make it an heirloom that banks a tiny permanent damage bonus every few kills. |
| Taming (`taming`) | `tame-health` | Tame Health | Increase your tamed animal health. |
| Taming (`taming`) | `tame-damage` | Tame Damage | Increase your tamed animal damage dealt. |
| Taming (`taming`) | `tame-health-regeneration` | Tame Regeneration | Increase your tamed animal regeneration. |
| Taming (`taming`) | `tame-pack-leader-aura` | Pack Leader Aura | Nearby tamed companions gain speed and regeneration near their owner. |
| Taming (`taming`) | `tame-beast-recall` | Beast Recall | Sneak-right-click with a lead to recall your nearest tamed companion to a safe nearby spot. Each recall costs hunger. |
| Taming (`taming`) | `tame-shared-pain` | Shared Pain | Spread a portion of your incoming damage across nearby owned companions without reducing them below their health floor. |
| Taming (`taming`) | `tame-mounted-tactics` | Mounted Tactics | Gain mount-specific combat and handling bonuses while riding. |
| Taming (`taming`) | `tame-fetch` | Fetch | Your tamed wolves gather nearby dropped items and bring them straight to you. |
| Taming (`taming`) | `tame-alphas-command` | Alpha's Command | Sneak-left-click while holding a bone to raycast a target, mark it with a private red glow, and command nearby combat pets to focus it. Successful commands consume one bone. |
| Taming (`taming`) | `tame-guardian-instinct` | Guardian Instinct | Nearby pets leap to intercept projectiles aimed at you, taking reduced damage themselves. |
| Taming (`taming`) | `tame-stable-hand` | Stable Hand | Animals you tame or breed keep a permanent bias toward better speed, jump, health, and safe fall distance. |
| Taming (`taming`) | `tame-wild-empathy` | Wild Empathy | Taming succeeds more often, and neutral mobs are slower to anger at you. |
| Taming (`taming`) | `tame-battle-bond` | Battle Bond | When one of your pets lands a kill, you and the nearby pack gain brief strength, speed, and regeneration with a visible bond. |
| Taming (`taming`) | `tame-last-breath` | Last Breath | On a lethal hit a pet drops to 1 HP, is briefly invulnerable, and recalls to your side. |
| TragOul (`tragoul`) | `tragoul-thorns` | Thorns | Reflect damage back to your attacker. |
| TragOul (`tragoul`) | `tragoul-globe` | Globe of Pain | Distributes outgoing damage across nearby enemies. |
| TragOul (`tragoul`) | `tragoul-healing` | Will of Pain | Every eligible attacker who damages you loses a small fixed amount of life, which is restored to you. |
| TragOul (`tragoul`) | `tragoul-lance` | Corpse Lances | Killing an enemy launches a red-tipped corpse lance that never targets you. Its damage is tripled while you wear no armor. |
| TragOul (`tragoul`) | `tragoul-blood-pact` | Blood Pact | Losing at least 2 hearts after armor, Resistance, and absorption can trigger random beneficial effects. |
| TragOul (`tragoul`) | `tragoul-bone-harvest` | Bone Harvest | Kills can spawn blood globes or bone snowball globes that grant buffs when picked up. |
| TragOul (`tragoul`) | `tragoul-corpse-explosion` | Corpse Explosion | Mobs you kill immediately display a blood nova and damage nearby hostile mobs. |
| TragOul (`tragoul`) | `tragoul-soul-siphon` | Soul Siphon | Every player-attributed damage source siphons part of its final damage as health. |
| TragOul (`tragoul`) | `tragoul-skeletal-servant` | Skeletal Servant | Sneak-right-click with bones to raise temporary skeletal servants, with one living servant allowed per level. Servants spawn with level-scaled random gear, inherit other TragOul perks, and hunt the last target you struck or that struck you. Summoning at the cap recycles the oldest servant and consumes a level-scaled number of bones. |
| TragOul (`tragoul`) | `tragoul-marrow-armor` | Marrow Armor | Bones in your inventory shatter to absorb part of incoming hits. |
| TragOul (`tragoul`) | `tragoul-curse-of-frailty` | Curse of Frailty | Attackers receive Weakness and, at higher levels, Slowness. |
| TragOul (`tragoul`) | `tragoul-death-sense` | Death Sense | Sense wounded creatures and players near you through walls with a private outline that intensifies in color as their health falls. |
| TragOul (`tragoul`) | `tragoul-plague-bearer` | Plague Bearer | Mobs that die poisoned or withered by you spread an amplified affliction across a large radius to any nearby mob you can damage. |
| TragOul (`tragoul`) | `tragoul-last-rites` | Last Rites | A killing blow leaves you at 1 HP as a fleeting spirit instead of dying. |
| Unarmed (`unarmed`) | `unarmed-sucker-punch` | Sucker Punch | Sprint punches, but more deadly. |
| Unarmed (`unarmed`) | `unarmed-power` | Unarmed Power | Your bare-handed strikes deal more damage. |
| Unarmed (`unarmed`) | `unarmed-glass-cannon` | Glass Cannon | Bonus Unarmed Damage the lower your armor value is. |
| Unarmed (`unarmed`) | `unarmed-battering-charge` | Battering Charge | Sprint into enemies with fists or a shield to deal impact damage. |
| Unarmed (`unarmed`) | `unarmed-combo-chain` | Combo Chain | Consecutive unarmed hits build combo stacks that increase punch damage. |
| Unarmed (`unarmed`) | `unarmed-disarm` | Disarm | Bare-hand hits can knock the held item out of players and mobs alike, and mobs may have a worn armor piece knocked loose too. |
| Unarmed (`unarmed`) | `unarmed-pressure-point` | Pressure Point | Bare-hand hits apply stacking slowness, with weakness at higher levels. |
| Unarmed (`unarmed`) | `unarmed-shockwave-clap` | Shockwave Clap | Sneak and punch the air to clap a shockwave that knocks back enemies in a cone. Each clap costs hunger. |
| Unarmed (`unarmed`) | `unarmed-iron-fists` | Iron Fists | Bare fists hit harder and punch through soft blocks faster. |
| Unarmed (`unarmed`) | `unarmed-grapple` | Grapple | Sneak-punch a mob or player to grab it, then hurl it where you look. Player grapples respect PvP protection. Each throw adds exhaustion. |
| Unarmed (`unarmed`) | `unarmed-second-wind` | Second Wind | Bare-hand kills restore hunger and grant a short regeneration burst. |
| Unarmed (`unarmed`) | `unarmed-meditation` | Meditation | Meditate while sneaking, still, and empty-handed to slowly build absorption hearts. |

## See also

- `03 - Player Usage.md`
- `02 - Concepts.md`
- Skill docs 11 through 33
