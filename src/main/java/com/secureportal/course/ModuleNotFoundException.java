package com.secureportal.course;

import java.util.UUID;

public class ModuleNotFoundException extends RuntimeException {

    public ModuleNotFoundException(UUID id) {
        super("A module was not found: " + id);
    }
}
