-- [ZEDGROUND] Armor Pack - body locations (ported from KATTAJ1 Clothes Core)
require "NPCs/BodyLocations"

local group = BodyLocations.getGroup("Human")

group:getOrCreateLocation(ZedArmor_BodyLocation.TorsoExtraPelvic)
group:getOrCreateLocation(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic)
group:getOrCreateLocation(ZedArmor_BodyLocation.Elbows)
group:getOrCreateLocation(ZedArmor_BodyLocation.Knees)

-- TorsoExtraPelvic
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ZedArmor_BodyLocation.TorsoExtraShoulderPelvic)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.FULL_SUIT_HEAD)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.FULL_SUIT)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.FULL_TOP)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.BOILERSUIT)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.BATH_ROBE)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.BODY_COSTUME)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.TORSO_EXTRA_VEST)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.TORSO_EXTRA)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraPelvic, ItemBodyLocation.TORSO_EXTRA_VEST_BULLET)

-- TorsoExtraShoulderPelvic
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ZedArmor_BodyLocation.TorsoExtraPelvic)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.SHOULDERPAD_RIGHT)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.SHOULDERPAD_LEFT)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.FULL_SUIT_HEAD)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.FULL_SUIT)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.FULL_TOP)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.BOILERSUIT)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.BATH_ROBE)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.BODY_COSTUME)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.TORSO_EXTRA_VEST)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.TORSO_EXTRA)
group:setExclusive(ZedArmor_BodyLocation.TorsoExtraShoulderPelvic, ItemBodyLocation.TORSO_EXTRA_VEST_BULLET)

-- Elbows / Knees (same rules as Clothes Core, minus its own LowerArms / LowerLegs locations)
group:setExclusive(ZedArmor_BodyLocation.Elbows, ItemBodyLocation.ELBOW_LEFT)
group:setExclusive(ZedArmor_BodyLocation.Elbows, ItemBodyLocation.ELBOW_RIGHT)
group:setExclusive(ZedArmor_BodyLocation.Knees, ItemBodyLocation.KNEE_LEFT)
group:setExclusive(ZedArmor_BodyLocation.Knees, ItemBodyLocation.KNEE_RIGHT)

print("[ZedArmor] body locations registered: ZedArmor:TorsoExtraPelvic, ZedArmor:TorsoExtraShoulderPelvic, ZedArmor:Elbows, ZedArmor:Knees")
