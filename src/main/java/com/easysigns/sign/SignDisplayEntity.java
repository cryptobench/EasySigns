package com.easysigns.sign;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Simple entity used to display floating text for signs.
 * This entity has no model and is invisible - only the nameplate is visible.
 */
public class SignDisplayEntity extends Entity {

    /**
     * Codec for serialization/deserialization.
     * Inherits from Entity.CODEC and adds no extra fields.
     */
    public static final BuilderCodec<SignDisplayEntity> CODEC = BuilderCodec
        .builder(SignDisplayEntity.class, SignDisplayEntity::new, Entity.CODEC)
        .build();

    public SignDisplayEntity(World world) {
        super(world);
    }

    public SignDisplayEntity() {
        super();
    }
}
