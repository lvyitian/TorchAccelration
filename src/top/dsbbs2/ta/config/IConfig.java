package top.dsbbs2.ta.config;

import java.io.IOException;

public interface IConfig<T> {
	public void autoCreateNewFile() throws IOException;
	public void loadConfig() throws IOException;
	public void saveConfig() throws IOException;
	public T getConfig();
}