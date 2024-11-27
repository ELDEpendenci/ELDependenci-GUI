package com.ericlam.mc.eldgui.component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.text.StringSubstitutor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.Nullable;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.modifier.Clickable;
import com.ericlam.mc.eldgui.component.modifier.Listenable;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;

public final class NumInputField<T extends Number> extends AbstractComponent implements Clickable, Listenable<AsyncChatEvent> {

	private final static Map<Class<? extends Number>, MathCalculate<? extends Number>> valueMap = new ConcurrentHashMap<>();

	private static <N extends Number> void addNumberType(Class<N> type, MathCalculate<N> math) {
		valueMap.put(type, math);
	}

	static {
		addNumberType(Integer.class, new MathCalculate<>(
				Integer::sum,
				(a, b) -> a - b,
				Number::intValue,
				(value, max) -> value > max,
				(value, min) -> value < min,
				Integer::parseInt
		));

		addNumberType(Double.class, new MathCalculate<>(
				Double::sum,
				(a, b) -> a - b,
				Number::doubleValue,
				(value, max) -> value > max,
				(value, min) -> value < min,
				Double::parseDouble
		));

		addNumberType(Long.class, new MathCalculate<>(
				Long::sum,
				(a, b) -> a - b,
				Number::longValue,
				(value, max) -> value > max,
				(value, min) -> value < min,
				Long::parseLong
		));

		addNumberType(Short.class, new MathCalculate<>(
				(a, b) -> (short) (a + b),
				(a, b) -> (short) (a - b),
				Number::shortValue,
				(value, max) -> value > max,
				(value, min) -> value < min,
				Short::parseShort
		));

		addNumberType(Float.class, new MathCalculate<>(
				Float::sum,
				(a, b) -> a - b,
				Number::floatValue,
				(value, max) -> value > max,
				(value, min) -> value < min,
				Float::parseFloat
		));

		addNumberType(Byte.class, new MathCalculate<>(
				(a, b) -> (byte) (a + b),
				(a, b) -> (byte) (a - b),
				Number::byteValue,
				(value, max) -> value > max,
				(value, min) -> value < min,
				Byte::parseByte
		));
	}

	private final T min, max, step;
	private final boolean disabled;
	private final String inputMessage;
	private final String errorMessage;
	private final long maxWait;
	private final MathCalculate<T> math;

	private T value;

	@SuppressWarnings("unchecked")
	public NumInputField(
			AttributeController attributeController,
			ItemStackService.ItemFactory itemFactory,
			@Nullable T min,
			@Nullable T max,
			@Nullable T step,
			boolean disabled,
			String inputMessage,
			String errorMessage,
			long maxWait,
			Class<T> numberType
	) {
		super(attributeController, itemFactory);
		this.math = (MathCalculate<T>) Optional.ofNullable(valueMap.get(numberType)).orElseThrow(() -> new IllegalStateException("unknown number type: " + numberType.getSimpleName()));
		this.min = Optional.ofNullable(min).orElse(math.toNumber.apply(0));
		this.max = Optional.ofNullable(max).orElse(math.toNumber.apply(64));
		this.step = Optional.ofNullable(step).orElse(math.toNumber.apply(1));
		this.disabled = disabled;
		this.inputMessage = inputMessage;
		this.errorMessage = errorMessage;
		this.maxWait = maxWait;
		Number num = (Number) Optional.ofNullable(attributeController.getAttribute(getItem(), AttributeController.VALUE_TAG)).orElse(0);
		this.value = math.toNumber.apply(num);
		itemFactory.lore("-> " + value);
		if (disabled) itemFactory.lore("&cDisabled");
	}

	@Override
	public void onClick(InventoryClickEvent event) {
		T value;
		if (event.isLeftClick()) {
			if (Objects.equals(this.value, max)) return;
			value = math.addNumber.apply(this.value, step);
			if (math.biggerThan.apply(value, max)) value = max;
		} else if (event.isRightClick()) {
			if (Objects.equals(this.value, min)) return;
			value = math.reduceNumber.apply(this.value, step);
			if (math.smallerThan.apply(value, min)) value = min;
		} else {
			return;
		}
		this.updateValue(value);
	}

	@Override
	public boolean isDisabled() {
		return disabled;
	}

	@Override
	public void onListen(Player player) {
		Map<String, Object> properties = Map.of("min", min, "max", max);
		player.sendMessage(StringSubstitutor.replace(inputMessage, properties));
	}

	@Override
	public long getMaxWaitingTime() {
		return maxWait;
	}

	@Override
	public void callBack(AsyncChatEvent event) {
		String msg = ((TextComponent) event.message()).content();
		try {
			T value = math.parseNum.apply(msg);
			if (math.smallerThan.apply(value, min) || math.biggerThan.apply(value, max))
				throw new NumberFormatException();
			this.updateValue(value);
		} catch (NumberFormatException e) {
			event.getPlayer().sendMessage(errorMessage);
		}
	}

	@Override
	public Class<AsyncChatEvent> getEventClass() {
		return AsyncChatEvent.class;
	}

	@Override
	public boolean shouldActivate(InventoryClickEvent e) {
		return e.getClick() == ClickType.MIDDLE;
	}


	private void updateValue(T value) {
		this.value = value;
		attributeController.setAttribute(getItem(), AttributeController.VALUE_TAG, this.value);
		itemFactory.lore(List.of("-> " + this.value));
		this.updateInventory();
	}

	record MathCalculate<T extends Number>(
			BiFunction<T, T, T> addNumber,
			BiFunction<T, T, T> reduceNumber,
			Function<Number, T> toNumber,
			BiFunction<T, T, Boolean> biggerThan,
			BiFunction<T, T, Boolean> smallerThan,
			Function<String, T> parseNum
	) {
	}

}
