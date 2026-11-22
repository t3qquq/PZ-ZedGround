--[[ ZEDGROUND HUD : 필터 교체 중 손에 드는 방독면 모델
  원본: Mask Filter Indicator by movinow
  https://steamcommunity.com/sharedfiles/filedetails/?id=3832759095
  제작자 허락을 받아 ZEDGROUND 서버용으로 고쳐 내장했다 (11-22). 이 파일을 다른 곳에 재배포하지 말 것.
  원본과 다른 점: 전역·파일 이름(MaskFilterIndicator* -> ZedHUDMask*), 모드 옵션은 ZedHUDOptions.lua 로 합침,
  봉쇄 구역 독가스로 필터가 닳을 때도 막대가 깜빡인다, 로그 추가. 그 밖의 동작·수치는 원본 그대로.
]]

ZedHUDMaskHandModel = {}
local HM = ZedHUDMaskHandModel

HM.ATTACHMENT = "Bip01_Prop2"

HM.SOURCES = {
	["Base.Hat_GasMask"] = {
		offsetKey = "GasMask", texture = "Clothes/Hat/GasMask", scale = 1.0,
		mesh = "Static/Clothes/M_GasMask", meshNoFilter = "Static/Clothes/M_GasMask_FilterLess",
	},
	["Base.Hat_BuildersRespirator"] = {
		offsetKey = "BuildersRespirator", texture = "BuildersRespirator", scale = 1.0,
		mesh = "Static/Clothes/M_BuildersMask", meshNoFilter = "Static/Clothes/M_BuildersMask_nofilter",
	},
	["Base.Hat_ImprovisedGasMask"] = {
		offsetKey = "ImprovisedGasMask", texture = "Clothes/Hat/ImprovisedGasMask", scale = 1.0,
		mesh = "Static/Clothes/M_ImprovisedGasMask", meshNoFilter = "Static/Clothes/M_ImprovisedGasMask_nofilter",
	},
	["Base.Hat_NBCmask"] = {
		offsetKey = "NBCmask", texture = "NBC_Mask", scale = 0.4,
		mesh = "WorldItems/NBC_Mask_Ground", meshNoFilter = "WorldItems/NBC_Mask_Ground",
	},
	["Base.SCBA"] = {
		offsetKey = "SCBA", texture = "SCBA", scale = 0.125,
		mesh = "WorldItems/Clothing/Bob_SCBA_Ground", meshNoFilter = "WorldItems/Clothing/Bob_SCBA_noTank_Ground",
	},
	["Base.HazmatSuit"] = {
		offsetKey = "HazmatSuit", texture = "Clothes/Hazmat/Hazmat_Yellow", scale = 0.33,
		mesh = "WorldItems/Clothing/Bob_Hazmat_Ground", meshNoFilter = "WorldItems/Clothing/Bob_Hazmat_Ground",
	},
}

HM.OFFSETS = {
	GasMask            = { -0.05, -0.01, 0.09, -150, -5, -35 },
	BuildersRespirator = { -0.01, -0.03, 0.07, -120, -10, -35 },
	ImprovisedGasMask  = { -0.01, -0.01, 0.09, -150, -5, -35 },
	NBCmask            = {  0.02, 0.07, 0.05, -90, -5, -10 },
	SCBA               = {  0.10, 0.03, 0.15, -165, -125, -15 },
	HazmatSuit         = {  0.09, -0.29, 0.11, -90, -10, -45 },
}

HM.created = {}
HM.namesByKey = {}

function HM.getSource(mask)
	return mask and HM.SOURCES[mask:getFullType()] or nil
end

function HM.getOffsets(offsetKey)
	return HM.OFFSETS[offsetKey] or { 0, 0, 0, 0, 0, 0 }
end

local function defineModel(src, mesh)
	local key = mesh .. "|" .. src.texture
	local cached = HM.created[key]
	if cached ~= nil then return cached or nil end
	local name = "ZedHUDMask_" .. string.gsub(mesh, "[^%w]", "_")
	local o = HM.getOffsets(src.offsetKey)
	local body = "model " .. name .. " { mesh = " .. mesh
		.. ", texture = " .. src.texture .. ", scale = " .. tostring(src.scale) .. ", "
		.. "attachment " .. HM.ATTACHMENT .. " { "
		.. "offset = " .. o[1] .. " " .. o[2] .. " " .. o[3] .. ", "
		.. "rotate = " .. o[4] .. " " .. o[5] .. " " .. o[6] .. ", } }"
	local ok, err = pcall(function()
		local script = ModelScript.new()
		script:setModule(getScriptManager():getModule("Base"))
		script:InitLoadPP(name)
		script:Load(name, body)
		getScriptManager():addModelScript(script)
	end)
	if not ok then
	end
	HM.created[key] = ok and name or false
	if not ok then return nil end
	HM.namesByKey[src.offsetKey] = HM.namesByKey[src.offsetKey] or {}
	table.insert(HM.namesByKey[src.offsetKey], name)
	return name
end

function HM.getHandModel(mask, withFilter)
	local src = HM.getSource(mask)
	if not src then return nil end
	return defineModel(src, withFilter and src.mesh or src.meshNoFilter)
end

function HM.applyOffsets(offsetKey, o)
	HM.OFFSETS[offsetKey] = o
	for _, name in ipairs(HM.namesByKey[offsetKey] or {}) do
		local script = getScriptManager():getModelScript("Base." .. name)
		local att = script and script:getAttachmentById(HM.ATTACHMENT)
		if att then
			att:getOffset():set(o[1], o[2], o[3])
			att:getRotate():set(o[4], o[5], o[6])
		end
	end
end
