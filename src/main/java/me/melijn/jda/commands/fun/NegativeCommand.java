package me.melijn.jda.commands.fun;

import me.melijn.jda.Helpers;
import me.melijn.jda.blub.Category;
import me.melijn.jda.blub.Command;
import me.melijn.jda.blub.CommandEvent;
import me.melijn.jda.utils.ImageUtils;

import java.awt.image.BufferedImage;

import static me.melijn.jda.Melijn.PREFIX;

public class NegativeCommand extends Command {

    public NegativeCommand() {
        this.commandName = "negative";
        this.usage = PREFIX + commandName + " [image]";
        this.description = "change an image to negative";
        this.aliases = new String[]{"invert"};
        this.category = Category.FUN;
    }

    private ImageUtils imageUtils = new ImageUtils();

    @Override
    protected void execute(CommandEvent event) {
        if (event.getGuild() == null || Helpers.hasPerm(event.getMember(), this.commandName, 0)) {
            BufferedImage img = null;
            img = imageUtils.getBufferedImage(event, img);
            if (img == null) {
                event.reply("Unknown image");
                return;
            }
            /* >> Right shift in bits
                    p >> n     n is hier het aantal bits dat je de p verschuift naar rechts
                    & 0xff;    door & neem je alleen de laatste bits gespecifieerd door de hex erachter, in dit geval 0xff of 8 bits;
                    source: https://android.jlelse.eu/java-when-to-use-n-8-0xff-and-when-to-use-byte-n-8-2efd82ae7dd7
                    8bits = max 255
                     */
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int pixel = img.getRGB(x, y);

                    int a = (pixel >> 24) & 0xff;
                    int r = (pixel >> 16) & 0xff;
                    int g = (pixel >> 8) & 0xff;
                    int b = pixel & 0xff;

                    r = 255 - r;
                    g = 255 - g;
                    b = 255 - b;
                    pixel = (a << 24) | (r << 16) | (g << 8) | b;

                    img.setRGB(x, y, pixel);
                }
            }
            imageUtils.sendImage(event, img);
        } else {
            event.reply("You need the permission `" + commandName + "` to execute this command.");
        }
    }
}