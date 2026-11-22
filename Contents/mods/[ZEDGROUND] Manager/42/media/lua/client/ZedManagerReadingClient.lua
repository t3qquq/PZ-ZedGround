-- Manager : 책 구간 배율 - 우클릭 메뉴 쪽 하한 제한 제거 (클라)
-- 규칙과 배율 처리는 shared/ZedManagerReading.lua 머리말 참고.
--
-- 바닐라 42.22.2 media/lua/client/ISUI/ISInventoryPaneContextMenu.lua
--   doLiteratureMenu : 하한에 걸린 책은 "너무 복잡함" 툴팁이 달린 비활성 "읽기" 를 넣고 진짜 읽기 항목을 빼버린다.
--                      감싸서, 막힌 이유가 하한뿐이면 그 비활성 항목을 바닐라 읽기 항목과 같게 바꾼다.
--                      (어두움·문맹·이미 아는 내용 같은 다른 이유가 하나라도 섞이면 바닐라 그대로 둔다)
--   onLiteratureItems: 하한 조건 한 줄만 ZedManager.Reading.tooHard 로 바꾼 사본 (샌드박스가 꺼져 있으면 원본)

require "ZedManagerReading"
require "ISUI/ISInventoryPaneContextMenu"

local ZR = ZedManager.Reading
local function log(msg) ZedManager.log("reading: " .. tostring(msg)) end

local origDoLiterature = ISInventoryPaneContextMenu.doLiteratureMenu
local origOnLiterature = ISInventoryPaneContextMenu.onLiteratureItems

function ISInventoryPaneContextMenu.doLiteratureMenu(context, items, player)
    local before = context.numOptions
    local r = origDoLiterature(context, items, player)
    if not ZR.enabled() then return r end

    local ok, err = pcall(function()
        local tooHardText = getText("ContextMenu_TooComplicated")
        local readText = getText("ContextMenu_Read")
        local blocked, other = {}, false
        for i = before, context.numOptions - 1 do
            local opt = context.options[i]
            if opt and opt.name == readText then
                local desc = opt.toolTip and opt.toolTip.description
                if opt.notAvailable and desc == tooHardText then
                    table.insert(blocked, i)
                else
                    other = true   -- 다른 이유로 막혔거나, 이미 읽을 수 있는 항목이 있다
                end
            end
        end
        if #blocked == 0 or other then return end

        -- 첫 항목을 바닐라 읽기 항목과 같게 바꾸고, 나머지(여러 권 선택 시 권마다 하나씩 생긴 것)는 지운다
        local first = context.options[blocked[1]]
        first.notAvailable = false
        first.toolTip = nil
        first.target = items
        first.onSelect = ISInventoryPaneContextMenu.onLiteratureItems
        first.param1 = player
        for j = #blocked, 2, -1 do
            local idx = blocked[j]
            table.insert(context.optionPool, context.options[idx])
            table.remove(context.options, idx)
            context.numOptions = context.numOptions - 1
        end
        for i, v in ipairs(context.options) do v.id = i end
        context:calcHeight()
        context:setWidth(context:calcWidth())
    end)
    if not ok then log("context menu patch FAILED err=" .. tostring(err) .. " -> vanilla menu kept") end
    return r
end

-- 42.22.2 사본. 바뀐 곳: 하한 조건 -> ZR.tooHard (문맹 판정은 원래 위 줄에 있어 그대로)
ISInventoryPaneContextMenu.onLiteratureItems = function(items, player)
    if not ZR.enabled() then return origOnLiterature(items, player) end
    local playerObj = getSpecificPlayer(player)
    items = ISInventoryPane.getActualUniqueItems(items)
    local readableItems = {}
    for i,k in ipairs(items) do
        local invalid = false
        if (playerObj:hasTrait(CharacterTrait.ILLITERATE) and not k:hasTag(ItemTag.PICTUREBOOK) and not k:hasTag(ItemTag.PICTURE))
        or (k:getLvlSkillTrained() ~= -1 and SkillBook[k:getSkillTrained()] and ZR.tooHard(playerObj, k))
        or k:hasTag(ItemTag.UNINTERESTING) then
            invalid = true
        end
        if not invalid then
            table.insert(readableItems, k)
        end
    end
    for i,k in ipairs(readableItems) do
        ISInventoryPaneContextMenu.readItem(k, player)
    end
end
