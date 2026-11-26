package com.omni.entity;

import com.omni.Omni;
import com.omni.entity.ai.SoldierCombatGoal;
import com.omni.weapon.BlasterItem;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Soldat : ennemi armé d'un blaster (mêmes armes JSON et même logique de tir que le joueur).
 * <ul>
 *   <li>Garde ses distances, mitraille par rafales en se déplaçant latéralement.</li>
 *   <li>Va se mettre à couvert quand son arme surchauffe ou qu'il est blessé.</li>
 *   <li>Prévient les soldats proches quand il repère une cible (« radio »).</li>
 *   <li>Ses tirs traversent ses alliés (pas de tir ami).</li>
 * </ul>
 */
public class SoldierEntity extends Monster {
    private static final double SQUAD_ALERT_RADIUS = 20;

    private SoldierCombatGoal combatGoal; // assigné dans registerGoals (appelé par le constructeur de Mob)

    public SoldierEntity(EntityType<? extends SoldierEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setDropChance(EquipmentSlot.MAINHAND, 0.1F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ARMOR, 4);
    }

    @Override
    protected void registerGoals() {
        combatGoal = new SoldierCombatGoal(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, combatGoal);
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(SoldierEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        if (getMainHandItem().isEmpty()) {
            float roll = random.nextFloat();
            String weapon = roll < 0.5F ? "blaster_rifle" : roll < 0.85F ? "blaster_pistol" : "heavy_repeater";
            setItemSlot(EquipmentSlot.MAINHAND, BlasterItem.create(Omni.id(weapon)));
        }
        return data;
    }

    public ItemStack weapon() {
        return getMainHandItem();
    }

    /** Pour les tests et le débogage : le soldat est-il en train de se replier / à couvert ? */
    public boolean isTakingCover() {
        return combatGoal != null && combatGoal.isInCover();
    }

    /** Les soldats sont tous du même camp (en plus des équipes du scoreboard). */
    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof SoldierEntity || super.isAlliedTo(other);
    }

    /** Transmet la cible aux soldats proches qui n'en ont pas encore. */
    public void alertSquad(LivingEntity target) {
        AABB area = getBoundingBox().inflate(SQUAD_ALERT_RADIUS);
        for (SoldierEntity ally : level().getEntitiesOfClass(SoldierEntity.class, area,
                s -> s != this && s.isAlive() && s.getTarget() == null && !s.isNoAi())) {
            ally.setTarget(target);
        }
    }
}
