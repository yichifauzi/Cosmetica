package com.eyezah.cosmetics.screens;

import cc.cosmetica.api.CosmeticType;
import cc.cosmetica.api.CustomCosmetic;
import cc.cosmetica.api.UserSettings;
import com.eyezah.cosmetics.cosmetics.model.CosmeticStack;
import com.eyezah.cosmetics.screens.fakeplayer.FakePlayer;
import com.eyezah.cosmetics.utils.Debug;
import com.eyezah.cosmetics.utils.TextComponents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CustomiseCosmeticsScreen extends ViewCosmeticsScreen {
	public CustomiseCosmeticsScreen(Screen parentScreen, FakePlayer player, UserSettings settings, boolean inlineChangeButton) {
		this(parentScreen, player, new ServerOptions(settings), inlineChangeButton, 1.0);
		this.canCloseWithBn = true;
	}

	protected CustomiseCosmeticsScreen(Screen parentScreen, FakePlayer player, ServerOptions options, boolean inlineChangeButton, double transitionProgress) {
		super(TextComponents.translatable("cosmetica.customizeCosmetics"), parentScreen, player, options, transitionProgress);
		this.inlineChangeButton = inlineChangeButton;
		this.leftMouseGrabBuffer = 51;
	}

	private final boolean inlineChangeButton;
	private boolean canCloseWithBn = false;

	public boolean canCloseWithBn() {
		return this.canCloseWithBn;
	}

	@Override
	protected <T extends CustomCosmetic, E> ViewCosmeticsScreen.Section createActiveSection(String title, List<String> items, @Nullable CosmeticType<T> type, @Nullable CosmeticStack<E> stack) {
		Button.OnPress onChange = type == null ? b -> this.minecraft.setScreen(new SelectLoreScreen(this, items.isEmpty() ? "" : items.get(0))) :
				b -> this.minecraft.setScreen(new BrowseCosmeticsScreen(this, type, stack));
		Div section = Div.create(title);
		Component headerText = TextComponents.translatable("cosmetica.entry." + title.replace(" ", ""));

		if (!this.inlineChangeButton) {
			Span header = section.addChild(new Span(0, 0, 200, 20, TextComponents.literal(title + "Header")));

			this.addTextTo(header, headerText, this.font.width(headerText) + 8, false);
			header.addChild(new Button(0, 0, 60, 20, TextComponents.translatable("cosmetica.change"), onChange));
		}
		else {
			this.addTextTo(section, headerText, 200, false);
		}

		if (items.isEmpty()) {
			this.addTextTo(section, TextComponents.translatable("cosmetica.entry.none"), 200, false).active = false;
		}
		else {
			for (String item : items) {
				this.addTextTo(section, TextComponents.literal(item), 200, false).active = false;
			}
		}

		if (this.inlineChangeButton) {
			section.addChild(new Button(0, 0, 100, 20, TextComponents.translatable("cosmetica.change"), onChange));
		}

		section.calculateDimensions();
		return section;
	}
}
