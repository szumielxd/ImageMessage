package com.bobacadodl.imgmessage;

import org.bukkit.entity.Player;
import org.bukkit.util.ChatPaginator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: bobacadodl
 * Date: 1/25/14
 * Time: 10:28 PM
 */
public class ImageMessage {
	private final static char TRANSPARENT_CHAR = ' ';

	private final Component[] lines;

	public ImageMessage(@NotNull BufferedImage image, int height, char imgChar) {
		TextColor[][] chatColors = toChatColorArray(image, height);
		this.lines = this.toImgMessage(chatColors, imgChar);
	}

	public ImageMessage(@NotNull TextColor[][] chatColors, char imgChar) {
		this.lines = this.toImgMessage(chatColors, imgChar);
	}

	public ImageMessage(@NotNull Component... imgLines) {
		this.lines = imgLines;
	}

	public @NotNull ImageMessage appendText(int startLine, @NotNull Component... texts) {
		int i = 0;
		for (int y = startLine; y < this.lines.length; y++) {
			if (texts.length > i) {
				this.lines[y] = this.lines[y].append(Component.text(" ")).append(texts[i++]);
			}
		}
		return this;
	}
	
	public ImageMessage appendText(Component... text) {
		return this.appendText(0, text);
	}
	
	public ImageMessage appendText(int startLine, @NotNull List<Component> texts) {
		return this.appendText(startLine, texts.toArray(new Component[0]));
	}
	
	public ImageMessage appendText(@NotNull List<Component> texts) {
		return this.appendText(0, texts);
	}

	public @NotNull ImageMessage appendCenteredText(@NotNull Component... text) {
		for (int y = 0; y < this.lines.length; y++) {
			if (text.length > y) {
				int len = ChatPaginator.AVERAGE_CHAT_PAGE_WIDTH - this.calcKnownLength(this.lines[y]);
				this.lines[y] = this.lines[y].append(center(text[y], len));
			} else {
				return this;
			}
		}
		return this;
	}

	private @NotNull TextColor[][] toChatColorArray(@NotNull BufferedImage image, int height) {
		double ratio = (double) image.getHeight() / image.getWidth();
		int width = (int) (height / ratio);
		if (width > 10) width = 10;
		BufferedImage resized = resizeImage(image, (int) (height / ratio), height);

		TextColor[][] chatImg = new TextColor[resized.getWidth()][resized.getHeight()];
		for (int x = 0; x < resized.getWidth(); x++) {
			for (int y = 0; y < resized.getHeight(); y++) {
				int rgb = resized.getRGB(x, y);
				Color c = new Color(rgb, true);
				TextColor closest = c.getAlpha() < 128 ? null : TextColor.color(c.getRed(), c.getGreen(), c.getBlue());
				chatImg[x][y] = closest;
			}
		}
		return chatImg;
	}

	private @NotNull Component[] toImgMessage(@NotNull TextColor[][] colors, char imgchar) {
		Component[] lines = new Component[colors[0].length];
		for (int y = 0; y < colors[0].length; y++) {
			Component line = Component.empty();
			for (int x = 0; x < colors.length; x++) {
				TextColor color = colors[x][y];
				line = line.append(color != null ? Component.text(imgchar, colors[x][y]) : Component.text(TRANSPARENT_CHAR));
			}
			lines[y] = line;
		}
		return lines;
	}

	private @NotNull BufferedImage resizeImage(@NotNull BufferedImage originalImage, int width, int height) {
		AffineTransform af = new AffineTransform();
		af.scale(
				width / (double) originalImage.getWidth(),
				height / (double) originalImage.getHeight());

		AffineTransformOp operation = new AffineTransformOp(af, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		return operation.filter(originalImage, null);
	}
	
	private int calcKnownLength(@NotNull Component comp) {
		int length = 0;
		if (comp instanceof TextComponent) {
			length += ((TextComponent)comp).content().length();
		}
		length += comp.children().stream().mapToInt(this::calcKnownLength).sum();
		return length;
	}
	
	private @NotNull Component trimEnd(@NotNull Component comp, int end) {
		return this.trimEnd(comp, end, new AtomicInteger());
	}
	
	private @NotNull Component trimEnd(@NotNull Component comp, int end, @NotNull AtomicInteger index) {
		if (end <= index.intValue()) return Component.empty();
		if (comp instanceof TextComponent) {
			int diff = end - index.intValue();
			TextComponent text = (TextComponent) comp;
			if (diff > text.content().length()) {
				diff -= text.content().length();
				index.addAndGet(text.content().length());
			} else {
				text = text.content(text.content().substring(0, diff));
				index.addAndGet(diff);
				return text.children(Collections.emptyList());
			}
		}
		List<Component> childs = comp.children();
		List<Component> newChilds = Collections.emptyList();
		if (childs.isEmpty()) return comp;
		for (Component child : childs) {
			newChilds.add(this.trimEnd(child, end, index));
			if (end <= index.intValue()) break;
		}
		return comp.children(newChilds);
	}

	private @NotNull Component center(@NotNull Component comp, int length) {
		int l = calcKnownLength(comp);
		if (l > length) {
			return this.trimEnd(comp, length);
		} else if (l == length) {
			return comp;
		} else {
			int leftPadding = (length - l) / 2;
			StringBuilder leftBuilder = new StringBuilder();
			for (int i = 0; i < leftPadding; i++) {
				leftBuilder.append(" ");
			}
			return Component.text(leftBuilder.toString()).append(comp);
		}
	}

	public @NotNull Component[] getLines() {
		return this.lines.clone();
	}

	public void sendToPlayer(@NotNull Player... players) {
		this.sendToPlayer(Identity.nil(), players);
	}
	
	public void sendToPlayer(@Nullable UUID sender, @NotNull Player... players) {
		this.sendToPlayer(sender == null ? Identity.nil() : Identity.identity(sender), players);
	}
	
	public void sendToPlayer(@NotNull Identity sender, @NotNull Player... players) {
		if (this.lines == null || this.lines.length == 0) return;
		Component comp = this.lines[0];
		for (int i = 1; i < this.lines.length; i++) {
			comp = comp.append(Component.newline()).append(this.lines[i]);
		}
		for (int i = 0; i < players.length; i++) {
			players[i].sendMessage(sender, comp);
		}
	}
}
