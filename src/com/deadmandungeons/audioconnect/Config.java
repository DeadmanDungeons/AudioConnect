package com.deadmandungeons.audioconnect;

import org.deadmandungeons.deadmanplugin.filedata.DeadmanConfig;
import org.deadmandungeons.deadmanplugin.filedata.DeadmanConfig.ConfigEnum;
import org.deadmandungeons.deadmanplugin.filedata.DeadmanConfig.ValueType;

public class Config {
	
	private static final DeadmanConfig handler = new DeadmanConfig(AudioConnect.getInstance());
	
	private Config() {}
	
	static {
		try {
			reload();
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	
	public static void reload() {
		handler.loadValues(ConfigNum.class, Number.class);
	}
	
	
	public enum ConfigNum implements ConfigEnum<ConfigNum, Number> {
		RECONNECT_INTERVAL("reconnect.interval", ValueType.SINGLE),
		RECONNECT_MAX_INTERVAL("reconnect.max-interval", ValueType.SINGLE),
		RECONNECT_DELAY("reconnect.delay", ValueType.SINGLE),
		RECONNECT_MAX_ATTEMPTS("reconnect.max-attempts", ValueType.SINGLE);
		
		private final String path;
		private final ValueType valueType;
		
		private ConfigNum(String path, ValueType valueType) {
			this.path = path;
			this.valueType = valueType;
		}
		
		@Override
		public String getPath() {
			return path;
		}
		
		@Override
		public ValueType getValueType() {
			return valueType;
		}
	}
	
	public static <E extends Enum<E> & ConfigEnum<E, T>, T> T value(E configEnum) {
		return handler.getValue(configEnum);
	}
	
}
