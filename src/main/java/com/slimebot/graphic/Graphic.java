package com.slimebot.graphic;

import net.dv8tion.jda.api.utils.FileUpload;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class Graphic {

    protected final int width, height;
    private final BufferedImage image;
    private final Graphics2D graphics2D;

    public Graphic(int width, int height) {
        this.width = width;
        this.height = height;
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.graphics2D = image.createGraphics();
    }

    protected void finish() {
        try {
            drawGraphic(graphics2D);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void drawGraphic(Graphics2D graphics2D) throws Exception;

    public FileUpload getFile() throws IOException {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bos);
            return FileUpload.fromData(bos.toByteArray(), "image.png");
        }
    }


}
