package mosi.display;

import mosi.DefaultProps;
import mosi.display.hiderules.HideRule.Operator;
import mosi.display.hiderules.HideRules;
import mosi.display.hiderules.HideThresholdRule;
import mosi.display.hiderules.HideUnchangedRule;
import mosi.display.inventoryrules.InventoryRule;
import mosi.display.inventoryrules.InventoryRules;
import mosi.display.inventoryrules.ItemIdMatch;
import mosi.utilities.Coord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.gson.JsonObject;

public class DisplayUnitItem extends DisplayUnitBase {
    public static final String DISPLAY_ID = "DisplayUnitItem";
    public static final ResourceLocation countdown = new ResourceLocation(DefaultProps.mosiKey, "countdown.png");

    private boolean displayOnHud;
    // User assigned name to item for display. Should only be used for display when neccessary and not be null.
    public String nickname;
    // Frequency to search player inventory for updated item statistics, most commonly quantity
    private int updateFrequency = 20;

    // For display purposes
    private int textDisplayColor = 1030655;
    // Display ItemStack used when counting rules do not find an ItemStack
    private ItemStack missingDisplayStack;
    // Matching rules for Counting
    private InventoryRules countingRules;
    private HideRules hidingRules;
    private TrackMode trackMode;

    boolean displayAnalogBar = true;
    boolean displayNumericCounter = true;

    // Information required to display
    private DisplayStats displayStats;
    private DisplayStats prevDisplayStat;

    private Coord analogOffset = new Coord(16, 13);
    private Coord digitalOffset = new Coord(16, -4);

    public DisplayUnitItem() {
        displayOnHud = true;
        nickname = "";
        trackMode = TrackMode.QUANTITY;
        countingRules = new InventoryRules();
        countingRules.addRule(new ItemIdMatch("grass", true));
        hidingRules = new HideRules();
        hidingRules.addRule(new HideUnchangedRule(30, false, Operator.AND));
        hidingRules.addRule(new HideThresholdRule(10, true, false, Operator.AND));
        missingDisplayStack = new ItemStack(Blocks.dirt);
    }

    /* Changes the quality that is being counted */
    public enum TrackMode {
        DURABILITY, QUANTITY, DURATION; // Duration not needed as PotionDisplayUnit will need to be seperate?
    }

    public static class DisplayStats {
        public final ItemStack stackToDisplay;
        public final int trackedCount;
        public final int maximumCount;

        public DisplayStats(ItemStack stackToDisplay, int trackedCount, int maximumCount) {
            this.stackToDisplay = stackToDisplay.copy();
            this.trackedCount = trackedCount;
            this.maximumCount = maximumCount;
        }
    }

    @Override
    public String getType() {
        return DISPLAY_ID;
    }

    @Override
    public Coord getOffset() {
        return new Coord(0, 0);
    }

    @Override
    public Coord getSize() {
        return new Coord(largestXDistance(getOffset().x, analogOffset.x, digitalOffset.x), largestZDistance(
                getOffset().z, analogOffset.z, digitalOffset.z));
    }

    private int largestXDistance(int iconCoord, int anaOffset, int digOffset) {
        // icon is 16x16 and its base is origin (0,0) for analog and digital offsets
        int farEdgePointAnalog = anaOffset >= 0 ? anaOffset + 16 : anaOffset;
        int farEdgePointDigit = digOffset >= 0 ? digOffset + 8 : digOffset;
        if (farEdgePointAnalog >= 0 && farEdgePointDigit >= 0) {
            return Math.max(Math.max(farEdgePointAnalog, farEdgePointDigit), 16);
        } else if (farEdgePointAnalog >= 0) {
            return Math.max(Math.max(farEdgePointAnalog, farEdgePointAnalog - farEdgePointDigit),
                    16 - farEdgePointDigit);
        } else if (farEdgePointDigit >= 0) {
            return Math.max(Math.max(farEdgePointDigit, farEdgePointDigit - farEdgePointAnalog),
                    16 - farEdgePointAnalog);
        } else {
            // Else Case both are < 0
            return Math.max(16 - farEdgePointAnalog, 16 - farEdgePointDigit);
        }
    }

    private int largestZDistance(int iconCoord, int anaOffset, int digOffset) {
        // icon is 16x16 and its base is origin (0,0) for analog and digital offsets
        int farEdgePointAnalog = anaOffset >= 0 ? anaOffset + 4 : anaOffset;
        int farEdgePointDigit = digOffset >= 0 ? digOffset + 8 : digOffset;
        if (farEdgePointAnalog >= 0 && farEdgePointDigit >= 0) {
            return Math.max(Math.max(farEdgePointAnalog, farEdgePointDigit), 16);
        } else if (farEdgePointAnalog >= 0) {
            return Math.max(Math.max(farEdgePointAnalog, farEdgePointAnalog - farEdgePointDigit),
                    16 - farEdgePointDigit);
        } else if (farEdgePointDigit >= 0) {
            return Math.max(Math.max(farEdgePointDigit, farEdgePointDigit - farEdgePointAnalog),
                    16 - farEdgePointAnalog);
        } else {
            // Else Case both are < 0
            return Math.max(16 - farEdgePointAnalog, 16 - farEdgePointDigit);
        }
    }

    @Override
    public VerticalAlignment getVerticalAlignment() {
        return VerticalAlignment.CENTER_ABSO;
    }

    @Override
    public HorizontalAlignment getHorizontalAlignment() {
        return HorizontalAlignment.LEFT_ABSO;
    }

    @Override
    public void onUpdate(Minecraft mc, int ticks) {
        if (ticks % updateFrequency == 0) {
            prevDisplayStat = displayStats;
            displayStats = calculateDisplayStats(mc);
            hidingRules.update(displayStats.trackedCount, prevDisplayStat.trackedCount);
            if (displayStats != null) {
                displayOnHud = !hidingRules.shouldHide(displayStats.trackedCount);
            } else {
                displayOnHud = false;
            }
        }
    }

    private DisplayStats calculateDisplayStats(Minecraft mc) {
        ItemStack stackToDisplay = missingDisplayStack;
        int trackedCount = 0;
        boolean foundMatch = false;
        RULE_LOOP: for (InventoryRule rule : countingRules) {
            ItemStack[] inventory = mc.thePlayer.inventory.mainInventory;
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] == null) {
                    continue;
                }
                ItemStack itemStack = inventory[i];
                if (rule.isMatch(itemStack, i, false, mc.thePlayer.inventory.currentItem == i)) {
                    if (!foundMatch) {
                        stackToDisplay = itemStack.copy();
                        foundMatch = true;
                    }
                    trackedCount += countStack(itemStack);
                    if (!rule.allowMultipleMatches()) {
                        continue RULE_LOOP;
                    }
                }
            }

            inventory = mc.thePlayer.inventory.armorInventory;
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] == null) {
                    continue;
                }
                ItemStack itemStack = inventory[i];
                if (rule.isMatch(itemStack, i, true, mc.thePlayer.inventory.currentItem == i)) {
                    if (!foundMatch) {
                        stackToDisplay = itemStack.copy();
                        foundMatch = true;
                    }
                    trackedCount += countStack(itemStack);
                    if (!rule.allowMultipleMatches()) {
                        continue RULE_LOOP;
                    }
                }
            }
        }

        int maximumCount;
        if (trackMode == TrackMode.DURABILITY) {
            maximumCount = stackToDisplay.getMaxDamage();
        } else {
            maximumCount = 64;
        }
        return new DisplayStats(stackToDisplay, trackedCount, maximumCount);
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return displayOnHud;
    }

    @Override
    public void renderDisplay(Minecraft mc, Coord position) {
        DisplayStats displayStats = getDisplayInfo(mc);
        if (displayStats == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float opacity = 1;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, opacity);
        RenderItem renderItem = new RenderItem();
        renderItem.zLevel = 200.0F;
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, displayStats.stackToDisplay,
                position.x, position.z);
        GL11.glDisable(GL11.GL_BLEND);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);

        GL11.glEnable(GL11.GL_BLEND);
        if (displayAnalogBar) {
            renderAnalogBar(mc, position, analogOffset, displayStats.trackedCount, displayStats.maximumCount);
        }
        GL11.glDisable(GL11.GL_BLEND);
        if (displayNumericCounter) {
            renderCounterBar(mc, position, digitalOffset, displayStats.trackedCount);
        }
        GL11.glPopMatrix();
    }

    public DisplayStats getDisplayInfo(Minecraft mc) {
        return displayStats;
    }

    private int countStack(ItemStack stackToCount) {
        if (trackMode == TrackMode.DURABILITY) {
            int currentDamage = stackToCount.getItemDamage();
            int maxDamage = stackToCount.getItem().getMaxDamage();
            return maxDamage - currentDamage;
        } else {
            return stackToCount.stackSize;
        }
    }

    /**
     * Used to Draw Analog Bar.
     * 
     * @param mc The Minecraft Instance
     * @param centerOfDisplay The Center Position where the bar needs to be offset From.
     * @param analogValue The value representing how full the Bar is
     * @param analogMax The value that represents the width of the full bar.
     */
    protected void renderAnalogBar(Minecraft mc, Coord centerOfDisplay, Coord offSet, int analogValue, int analogMax) {
        mc.renderEngine.bindTexture(countdown);
        int scaledValue = scaleAnalogizeValue(analogValue, analogMax);
        DisplayRenderHelper.drawTexturedModalRect(Tessellator.instance, 10.0f, centerOfDisplay.x + offSet.x,
                centerOfDisplay.z + offSet.z, 0, 0, 16, 3);
        if (scaledValue > 9) {
            DisplayRenderHelper.drawTexturedModalRect(Tessellator.instance, 10.0f, centerOfDisplay.x + offSet.x,
                    centerOfDisplay.z + offSet.z, 0, 3, scaledValue, 3);
        } else if (scaledValue > 4) {
            DisplayRenderHelper.drawTexturedModalRect(Tessellator.instance, 10.0f, centerOfDisplay.x + offSet.x,
                    centerOfDisplay.z + offSet.z, 0, 6, scaledValue, 3);
        } else {
            DisplayRenderHelper.drawTexturedModalRect(Tessellator.instance, 10.0f, centerOfDisplay.x + offSet.x,
                    centerOfDisplay.z + offSet.z, 0, 9, scaledValue, 3);
        }
    }

    /**
     * Scale a tracked value from range [0-analogMax] to fit the display bars resolution of [0-16]
     */
    private int scaleAnalogizeValue(int analogValue, int analogMax) {
        float scaledDuration = analogValue;
        if (analogValue > analogMax) {
            return 18;
        }
        if (analogValue < 0) {
            return 0;
        }
        return (int) ((float) (analogValue) / (float) (analogMax) * 18);
    }

    /**
     * Used to Draw Analog Bar.
     * 
     * @param mc The Minecraft Instance
     * @param fontRenderer The fontRenderer
     * @param centerOfDisplay The Center Position where the bar is offset From.
     * @param analogValue The value representing how full the Bar is
     * @param analogMax The value that represents the width of the full bar.
     */
    protected void renderCounterBar(Minecraft mc, Coord centerOfDisplay, Coord offSet, int counterAmount) {
        String displayAmount = Integer.toString(counterAmount);
        switch (getHorizontalAlignment()) {
        case CENTER_ABSO:
        case CENTER_PERC:
            mc.fontRenderer.drawString(displayAmount,
                    centerOfDisplay.x + 8 - mc.fontRenderer.getStringWidth(displayAmount) / 2 + offSet.x,
                    centerOfDisplay.z - offSet.z, textDisplayColor);
            break;
        case LEFT_ABSO:
        case LEFT_PERC:
            mc.fontRenderer.drawString(displayAmount, centerOfDisplay.x + offSet.x, centerOfDisplay.z - offSet.z,
                    textDisplayColor);
            break;
        case RIGHT_ABSO:
        case RIGHT_PERC:
            mc.fontRenderer.drawString(displayAmount, centerOfDisplay.x - mc.fontRenderer.getStringWidth(displayAmount)
                    + offSet.x, centerOfDisplay.z - offSet.z, textDisplayColor);
            break;
        }
    }

    /**
     * Helper method that Maps the real value provided (representing damage typically) to a different scale (typically
     * resolution, 16)
     * 
     * @param realValue represents value in Set 1
     * @param realMax is the max value in set 1, min value is assumed zero.
     * @param scaleMax is the max value in set 2, min value is assumed zero.
     * @return realValue in set 2
     */
    protected int mapValueToScale(int realValue, int realMax, int scaleMax) {
        return realValue > realMax ? scaleMax : realValue < 0 ? 0 : (int) (((float) realValue) / realMax * scaleMax);
    }

    @Override
    public JsonObject saveCustomData(JsonObject jsonObject) {
        return null;
    }

    @Override
    public void loadCustomData(DisplayUnitFactory factory, JsonObject customData) {

    }
}
