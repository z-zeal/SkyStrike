package io.github.skystrike.platform;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;

/** Per-user writable storage; never points at packaged assets. */
public interface StorageProvider {
    Preferences preferences(String name);
    FileHandle userMapsDir();
}
