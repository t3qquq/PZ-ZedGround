--[[ ZEDGROUND HUD : 방독면 필터 교체 (HUD 우클릭 / R 길게 / 총기 라디얼 메뉴)
  원본: Mask Filter Indicator by movinow
  https://steamcommunity.com/sharedfiles/filedetails/?id=3832759095
  제작자 허락을 받아 ZEDGROUND 서버용으로 고쳐 내장했다 (11-22). 이 파일을 다른 곳에 재배포하지 말 것.
  원본과 다른 점: 전역·파일 이름(MaskFilterIndicator* -> ZedHUDMask*), 모드 옵션은 ZedHUDOptions.lua 로 합침,
  봉쇄 구역 독가스로 필터가 닳을 때도 막대가 깜빡인다, 로그 추가. 그 밖의 동작·수치는 원본 그대로.
]]

require "ISUI/ISFirearmRadialMenu"
require "ISUI/ISInventoryPaneContextMenu"
require "ISUI/ISCraftingUI"
require "Entity/ISEntityUI"
require "TimedActions/ISBaseTimedAction"
require "TimedActions/ISUnequipAction"
require "TimedActions/ISWearClothing"
require "ZedHUDMaskHandModel"
require "ZedHUDMaskMotion"
require "ZedHUDConfig"

ZedHUDMask = {}
ZedHUDMask.disabled = false

local HOLD_MS = 500

-- ZEDGROUND (11-22 t3): 원본은 "필터 소진"·"예비 필터 없음"을 머리 위 말풍선(setHaloNote)으로 띄운다.
-- 1·3인칭(Viewpoint)에서는 머리 위가 안 보이므로 HUD 알림(ZedHUDMaskHUD.showNotice, 방독면 막대 위)으로 옮긴다.
local function notify(key, why)
	ZedHUD.log("mask: notice " .. tostring(key) .. " (" .. tostring(why) .. ")")
	ZedHUDMaskHUD.showNotice(getText(key))
end

local MASK_CONFIGS = {
	{ tagField = "GAS_MASK",            put = "PutFilterOnGasMask",         remove = "RemoveGasMaskFilter",            spareTagField = "GASMASK_FILTER" },
	{ tagField = "RESPIRATOR",          put = "PutFiltersOnRespirator",     remove = "RemoveRespiratorFilters",        spareTagField = "RESPIRATOR_FILTER" },
	{ tagField = "IMPROVISED_GAS_MASK", put = "PutRagInImprovisedGasMask",  remove = "RemoveRagFromImprovisedGasMask", spareFullType = "Base.RippedSheets" },
	{ tagField = "SCBA",                put = "put_oxygen_tank",            remove = "remove_oxygen_tank",             spareTagField = "OXYGEN_TANK", isTank = true },
}


function ZedHUDMask.findWornMask(playerObj)
	local worn = playerObj:getWornItems()
	for i = 0, worn:size() - 1 do
		local item = worn:get(i):getItem()
		if item and instanceof(item, "Clothing") then
			for _, cfg in ipairs(MASK_CONFIGS) do
				if cfg.itemTag and item:hasTag(cfg.itemTag) then
					return item, cfg
				end
			end
		end
	end
	return nil
end
local findWornMask = ZedHUDMask.findWornMask

function ZedHUDMask.hasDrainable(mask, cfg)
	if cfg and cfg.isTank then
		return mask:hasTank()
	end
	return mask:hasFilter()
end
local hasDrainable = ZedHUDMask.hasDrainable

function ZedHUDMask.canTurnOn(mask)
	return mask:isWorn() and mask:hasTank() and mask:getUsedDelta() > 0
end

function ZedHUDMask.toggleActivated(playerObj)
	local mask, cfg = findWornMask(playerObj)
	if not (mask and cfg and cfg.isTank) then return end
	if mask:isActivated() then
		mask:setActivated(false)
	elseif ZedHUDMask.canTurnOn(mask) then
		mask:setActivated(true)
	else
		return
	end
	syncItemActivated(playerObj, mask)
	mask:playActivateDeactivateSound()
end

local function isUsableSpare(item, cfg)
	if cfg.spareFullType then
		return item:getFullType() == cfg.spareFullType
	end
	if cfg.spareTag and item:hasTag(cfg.spareTag) then
		return not instanceof(item, "DrainableComboItem") or item:getCurrentUsesFloat() > 0
	end
	return false
end

local function spareRemaining(item, cfg)
	if cfg.spareFullType then
		local life = item:getModData().filterLife
		if type(life) == "number" then
			return life
		end
		return 1.0
	end
	if instanceof(item, "DrainableComboItem") then
		return item:getCurrentUsesFloat()
	end
	return 1.0
end

function ZedHUDMask.listSpareFilters(playerObj, cfg)
	local list = {}
	local containers = ISInventoryPaneContextMenu.getContainers(playerObj)
	if not containers then return list end
	for i = 0, containers:size() - 1 do
		local items = containers:get(i):getItems()
		for j = 0, items:size() - 1 do
			local item = items:get(j)
			if isUsableSpare(item, cfg) then
				table.insert(list, spareRemaining(item, cfg))
			end
		end
	end
	table.sort(list, function(a, b) return a > b end)
	return list
end

local function hasSpareFilter(playerObj, cfg)
	return #ZedHUDMask.listSpareFilters(playerObj, cfg) > 0
end

local function ownContainers(playerObj, allContainers)
	local inv = playerObj:getInventory()
	local list = ArrayList.new()
	for i = 0, allContainers:size() - 1 do
		local c = allContainers:get(i)
		if c == inv or c:getOutermostContainer() == inv then
			list:add(c)
		end
	end
	return list
end

local function findBestSpare(playerObj, cfg, ownOnly)
	local containers = ISInventoryPaneContextMenu.getContainers(playerObj)
	if not containers then return nil end
	if ownOnly then containers = ownContainers(playerObj, containers) end
	local best, bestLeft = nil, -1
	for i = 0, containers:size() - 1 do
		local items = containers:get(i):getItems()
		for j = 0, items:size() - 1 do
			local item = items:get(j)
			if isUsableSpare(item, cfg) then
				local left = spareRemaining(item, cfg)
				if left > bestLeft then best, bestLeft = item, left end
			end
		end
	end
	return best
end

local function filterPercent(mask)
	return math.floor(mask:getUsedDelta() * 100 + 0.5)
end


local function startCraft(playerObj, mask, cfg, recipeName, nextFn, cancelFn)
	local recipe = getScriptManager():getCraftRecipe(recipeName)
	if not recipe then return false end

	local allContainers = ISInventoryPaneContextMenu.getContainers(playerObj)
	local containers = ownContainers(playerObj, allContainers)
	local logic = HandcraftLogic.new(playerObj, nil, nil)
	logic:setIsoObject(logic:findCraftSurface(playerObj, 2))
	logic:setContainers(containers)
	logic:setRecipeFromContextClick(recipe, mask)
	if not logic:canPerformCurrentRecipe() and containers:size() < allContainers:size() then
		containers = allContainers
		logic:setContainers(containers)
		logic:setRecipeFromContextClick(recipe, mask)
	end
	if not logic:canPerformCurrentRecipe() then
		return false
	end

	local items = logic:getRecipeData():getAllInputItems()
	local itemsToReturn = logic:getRecipeData():getAllPutBackInputItems()
	if logic:isUsingRecipeAtHandBenefit() then
		local atHand = logic:getUsingRecipeAtHandItem()
		if atHand then
			items:add(atHand)
			itemsToReturn:add(atHand)
		end
	end

	local returnToContainer = {}
	if not recipe:isCanBeDoneFromFloor() then
		local itemsWereMoved = false
		for i = 1, items:size() do
			local item = items:get(i - 1)
			if item:getContainer() ~= playerObj:getInventory() then
				ISInventoryPaneContextMenu.transferIfNeeded(playerObj, item)
				if itemsToReturn:contains(item) then
					table.insert(returnToContainer, item)
				end
				itemsWereMoved = true
			end
		end
		if itemsWereMoved then
			logic:setRecipeFromContextClick(recipe, mask)
		end
	end

	local action = ISEntityUI.HandcraftStart(playerObj, logic, false, false)
	if action then
		if not playerObj:hasAwkwardHands() then
			action.stopOnWalk = false
		end
		if not isClient() then
			action:setOnStart(function(_, act)
				local name = ZedHUDMaskHandModel.getHandModel(mask, hasDrainable(mask, cfg))
				if name then act:setOverrideHandModels(nil, name) end
			end)
		end
		ISTimedActionQueue.add(action)
		local cancelled = false
		action:setOnCancel(function() cancelled = true end)
		action:setOnComplete(function()
			logic:stopCraftAction()
			if cancelled then
				if cancelFn then cancelFn() end
			elseif nextFn then
				nextFn()
			end
		end)
		logic:startCraftAction(action)
	end
	ISCraftingUI.ReturnItemsToOriginalContainer(playerObj, returnToContainer)
	return action ~= nil
end


local function newTakeOff(character, item, onAbort, onDone)
	local o = ISUnequipAction:new(character, item, 50)
	o.stop = function(self)
		ISUnequipAction.stop(self)
		if onAbort then onAbort() end
	end
	o.perform = function(self)
		ISUnequipAction.perform(self)
		if onDone then onDone() end
	end
	return o
end

local function newRestoreWear(character, item, onDone)
	local o = ISWearClothing:new(character, item)
	o.stopOnWalk = false
	o.stopOnRun = false
	if onDone then
		o.perform = function(self)
			ISWearClothing.perform(self)
			onDone()
		end
	end
	return o
end


local pendingTasks = {}

local function deferUntil(cond, fn, maxTicks)
	table.insert(pendingTasks, { cond = cond, fn = fn, left = maxTicks or 1 })
end

local function runPendingTasks()
	if #pendingTasks == 0 then return end
	local tasks = pendingTasks
	pendingTasks = {}
	for _, t in ipairs(tasks) do
		if t.cond() then
			t.fn()
		else
			t.left = t.left - 1
			if t.left > 0 then table.insert(pendingTasks, t) end
		end
	end
end

local function activateWhenWorn(playerObj, mask)
	deferUntil(function() return ZedHUDMask.canTurnOn(mask) end, function()
		if not mask:isActivated() then
			mask:setActivated(true)
			syncItemActivated(playerObj, mask)
			mask:playActivateDeactivateSound()
		end
	end, 150)
end

local function scheduleRestore(playerObj, mask, wasOn)
	deferUntil(function() return true end, function()
		if playerObj:isDead() then return end
		local onDone = wasOn and function() activateWhenWorn(playerObj, mask) end or nil
		if playerObj:isEquippedClothing(mask) then
			if onDone then onDone() end
		else
			if mask:getContainer() ~= playerObj:getInventory() then return end
			ISTimedActionQueue.add(newRestoreWear(playerObj, mask, onDone))
		end
	end, 1)
end

function ZedHUDMask.performSwap(playerObj)
	local mask, cfg = findWornMask(playerObj)
	if not mask then return end
	ZedHUD.log("mask: swap requested, mask=" .. tostring(mask:getFullType()) .. " left=" .. tostring(mask:getUsedDelta()))

	local noSpareKey = cfg.isTank and "UI_MFI_NoSpareTank" or "UI_MFI_NoSpareFilter"
	if not hasSpareFilter(playerObj, cfg) then
		notify(noSpareKey, "swap refused, no spare")
		return
	end

	local keepOn = ZedHUDMaskOptions.isKeepMaskOn() and not cfg.isTank
	local wasOn = cfg.isTank and mask:isActivated() or false
	local onAbort = nil
	if cfg.isTank then
		onAbort = function() scheduleRestore(playerObj, mask, wasOn) end
	end
	local function wearBack()
		if keepOn then
			ISTimedActionQueue.add(ZedHUDMaskMotion:new(playerObj, mask,
				getText("ContextMenu_Wear") .. " " .. mask:getName()))
		elseif cfg.isTank then
			local onDone = wasOn and function() activateWhenWorn(playerObj, mask) end or nil
			ISTimedActionQueue.add(newRestoreWear(playerObj, mask, onDone))
		else
			ISTimedActionQueue.add(ISWearClothing:new(playerObj, mask))
		end
	end
	local function startCrafts()
		local function putOrWearBack()
			if hasDrainable(mask, cfg) then
				wearBack()
				return
			end
			if not startCraft(playerObj, mask, cfg, cfg.put, wearBack, onAbort) then
				notify(noSpareKey, "put craft could not start")
				wearBack()
			end
		end
		if hasDrainable(mask, cfg) then
			if not startCraft(playerObj, mask, cfg, cfg.remove, putOrWearBack, onAbort) then
				wearBack()
			end
		else
			putOrWearBack()
		end
	end

	local function begin()
		local takeOff
		if keepOn then
			takeOff = ZedHUDMaskMotion:new(playerObj, mask,
				getText("ContextMenu_Unequip") .. " " .. mask:getName())
		elseif cfg.isTank then
			takeOff = newTakeOff(playerObj, mask, onAbort, function()
				deferUntil(function() return not playerObj:isEquippedClothing(mask) end, startCrafts, 150)
			end)
			takeOff.stopOnWalk = false
		else
			takeOff = ISUnequipAction:new(playerObj, mask, 50)
		end
		if playerObj:hasAwkwardHands() then takeOff.stopOnWalk = true end
		ISTimedActionQueue.add(takeOff)
		if not cfg.isTank then
			startCrafts()
		end
	end

	if not findBestSpare(playerObj, cfg, true) then
		local spare = findBestSpare(playerObj, cfg, false)
		if spare and spare:getContainer() then
			local pickUp = ISInventoryTransferUtil.newInventoryTransferAction(playerObj, spare,
				spare:getContainer(), playerObj:getInventory())
			local basePerform = pickUp.perform
			pickUp.perform = function(self)
				basePerform(self)
				deferUntil(function() return findBestSpare(playerObj, cfg, true) ~= nil end, function()
					if playerObj:isDead() or not playerObj:isEquippedClothing(mask) then return end
					begin()
				end, 150)
			end
			ISTimedActionQueue.add(pickUp)
			return
		end
	end
	begin()
end

function ZedHUDMask.unequipMask(playerObj)
	local mask = findWornMask(playerObj)
	if mask then
		ISTimedActionQueue.add(ISUnequipAction:new(playerObj, mask, 50))
	end
end

function ZedHUDMask.hasSpareFilterPublic(playerObj)
	local mask, cfg = findWornMask(playerObj)
	if not (mask and cfg) then return false end
	return hasSpareFilter(playerObj, cfg)
end


function ISFirearmRadialMenu.checkKey(key)
	local playerObj = getSpecificPlayer(0)
	if UIManager.getSpeedControls() and (UIManager.getSpeedControls():getCurrentGameSpeed() == 0) then
		return false
	end
	if not playerObj or playerObj:isDead() then
		return false
	end
	local queue = ISTimedActionQueue.queues[playerObj]
	if queue and #queue.queue > 0 then
		return false
	end
	if getCell():getDrag(0) then
		return false
	end
	if not getCore():isKey("ReloadWeapon", key) then
		return false
	end
	return true
end


local state = { downMS = nil, holdFired = false }

local function passesGuards(playerObj)
	if UIManager.getSpeedControls() and (UIManager.getSpeedControls():getCurrentGameSpeed() == 0) then
		return false
	end
	if not playerObj or playerObj:isDead() then
		return false
	end
	local queue = ISTimedActionQueue.queues[playerObj]
	if queue and #queue.queue > 0 then
		return false
	end
	if getCell():getDrag(0) then
		return false
	end
	return true
end

function ZedHUDMask.onKeyStartPressed(key)
	state.downMS = nil
	state.holdFired = false
	if ZedHUDMask.disabled then return end
	if not (getCore():isKey("ReloadWeapon", key) or getCore():isKey("SharpenWeapon", key)) then return end
	local playerObj = getSpecificPlayer(0)
	if not passesGuards(playerObj) then return end
	state.downMS = getTimestampMs()
end

function ZedHUDMask.onKeyKeepPressed(key)
	if ZedHUDMask.disabled then return end
	if state.holdFired or not state.downMS then return end
	if not getCore():isKey("ReloadWeapon", key) then return end
	if getTimestampMs() - state.downMS < HOLD_MS then return end
	state.holdFired = true
	local playerObj = getSpecificPlayer(0)
	if not passesGuards(playerObj) then return end
	if ISFirearmRadialMenu.checkWeapon(playerObj) then return end
	if getPlayerRadialMenu(0):isReallyVisible() then return end
	ZedHUDMask.performSwap(playerObj)
end

function ZedHUDMask.onKeyReleased(key)
	if ZedHUDMask.disabled then return end
	local downMS = state.downMS
	state.downMS = nil
	if not downMS or state.holdFired then return end
	if getTimestampMs() - downMS >= HOLD_MS then return end
	local playerObj = getSpecificPlayer(0)
	if not passesGuards(playerObj) then return end
	if getPlayerRadialMenu(0):isReallyVisible() then return end
	if getCore():isKey("SharpenWeapon", key) then
		local primary = playerObj:getPrimaryHandItem()
		if primary and primary:isSharpenable() then
			ISFirearmRadialMenu.sharpenWeapon(primary, playerObj)
		end
	end
end


local originalFillMenu = ISFirearmRadialMenu.fillMenu
function ISFirearmRadialMenu:fillMenu()
	originalFillMenu(self)
	if ZedHUDMask.disabled then return end
	local playerObj = self.character
	local mask, cfg = findWornMask(playerObj)
	if not mask then return end
	if not hasSpareFilter(playerObj, cfg) then return end
	local label = getText(cfg.isTank and "UI_MFI_RadialReplaceTank" or "UI_MFI_RadialReplaceFilter")
	if hasDrainable(mask, cfg) then
		label = label .. " (" .. filterPercent(mask) .. "%)"
	end
	local menu = getPlayerRadialMenu(self.playerNum)
	menu:addSlice(mask:getDisplayName() .. "\n" .. label, mask:getTex(), ZedHUDMask.onRadialSwap, self)
end

function ZedHUDMask.onRadialSwap(radial)
	ZedHUDMask.performSwap(radial.character)
end


local function onGameStart()
	-- 원본 모드가 같이 켜져 있으면 HUD·라디얼 항목이 두 벌이 된다 -> 이쪽을 끈다
	if MaskFilterIndicator ~= nil then
		ZedHUDMask.disabled = true
		ZedHUD.log("mask: original Mask Filter Indicator mod is active -> built-in copy DISABLED")
		return
	end
	local ok = true
	for _, cfg in ipairs(MASK_CONFIGS) do
		cfg.itemTag = ItemTag and ItemTag[cfg.tagField] or nil
		if not cfg.itemTag then
			ok = false
			ZedHUD.log("mask: ItemTag." .. tostring(cfg.tagField) .. " missing")
		end
		if cfg.spareTagField then
			cfg.spareTag = ItemTag and ItemTag[cfg.spareTagField] or nil
			if not cfg.spareTag then
				ok = false
				ZedHUD.log("mask: ItemTag." .. tostring(cfg.spareTagField) .. " missing")
			end
		end
		for _, recipeName in ipairs({ cfg.put, cfg.remove }) do
			if not getScriptManager():getCraftRecipe(recipeName) then
				ok = false
				ZedHUD.log("mask: craftRecipe " .. tostring(recipeName) .. " missing")
			end
		end
	end
	if not ok then
		ZedHUDMask.disabled = true
	end
	ZedHUD.log("mask: init " .. (ok and "ok" or "FAILED -> mask HUD disabled (see lines above)"))
end

local EMPTY_CHECK_TICKS = 30
local emptyCheckCounter = 0
local lastMaskId = nil
local lastHadAmount = false

local function checkDrainableEmpty()
	emptyCheckCounter = emptyCheckCounter + 1
	if emptyCheckCounter < EMPTY_CHECK_TICKS then return end
	emptyCheckCounter = 0
	if ZedHUDMask.disabled then return end
	local playerObj = getSpecificPlayer(0)
	if not playerObj or playerObj:isDead() then
		lastMaskId = nil
		return
	end
	local mask, cfg = findWornMask(playerObj)
	if not mask or not hasDrainable(mask, cfg) then
		lastMaskId = nil
		return
	end
	local id = mask:getID()
	local hasAmount = mask:getUsedDelta() > 0
	if id == lastMaskId and lastHadAmount and not hasAmount
			and ZedHUDMaskOptions and ZedHUDMaskOptions.isNotifyEmpty() then
		local key = cfg.isTank and "UI_MFI_TankEmpty" or "UI_MFI_FilterEmpty"
		notify(key, "worn " .. (cfg.isTank and "tank" or "filter") .. " ran out")
	end
	lastMaskId = id
	lastHadAmount = hasAmount
end

Events.OnGameStart.Add(onGameStart)
Events.OnTick.Add(checkDrainableEmpty)
Events.OnTick.Add(runPendingTasks)
Events.OnKeyStartPressed.Add(ZedHUDMask.onKeyStartPressed)
Events.OnKeyKeepPressed.Add(ZedHUDMask.onKeyKeepPressed)
Events.OnKeyPressed.Add(ZedHUDMask.onKeyReleased)
