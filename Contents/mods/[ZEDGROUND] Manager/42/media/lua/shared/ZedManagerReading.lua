-- Manager : 책 구간 배율 (서버·클라 공용)
--
-- 규칙 (11-22 t3, 샌드박스 ZedManager.ReadingBrackets)
--   책은 레벨과 상관없이 아무 권이나 읽을 수 있다 (하한 제한 제거). 상한(이미 그 책 범위를 넘은 레벨)은 바닐라대로 막는다.
--   읽어 둔 책은 자기가 맡은 레벨 구간에서만 바닐라 경험치 배율을 준다.
--     1권 = 0~1레벨, 2권 = 2~3, 3권 = 4~5, 4권 = 6~7, 5권 = 8~9 (바닐라 책 배율이 붙는 구간과 같다)
--   1·3·5권을 읽었으면 0~1·4~5·8~9 에서만 배율이 켜지고 2~3·6~7 에서는 없다.
--   부분 읽기는 바닐라처럼 10% 마다 최대 배율의 10%.
--
-- 왜 갈아 끼우나
--   바닐라는 스킬마다 배율을 하나만 들고 있고(XP.xpMapMultiplier), 지금 레벨이 그 책 구간인지 따지지 않고 곱한다(XP.AddXP).
--   책 최대 레벨에 닿을 때만 지운다. 그래서 0레벨이 3권 배율을 가지면 0~5레벨 전부에 붙는다.
--   -> 레벨이 바뀔 때·독서가 끝날 때·접속할 때 "지금 구간을 맡은 책"의 배율로 서버가 다시 넣는다 (ZR.refresh).
--   어느 책을 몇 쪽 읽었는지는 바닐라가 책 종류별로 저장해 둔다 (character:getAlreadyReadPages(fullType)).
--
-- 바닐라 42.22.2 media/lua/shared/TimedActions/ISReadABook.lua 를 덮어쓰는 함수: update, animEvent, checkLevel
--   하한 판정 한 줄(getLvlSkillTrained() > 레벨 + 1)만 ZR.tooHard 로 바꾼 사본이다. 바닐라가 바뀌면 다시 맞출 것.
--   샌드박스가 꺼져 있으면 바닐라 원본을 그대로 부른다.
-- 감싸는 함수: checkMultiplier (지금 구간 책일 때만 바닐라 실행), complete (끝나면 ZR.refresh)
-- 메뉴 쪽 하한 판정은 client/ZedManagerReadingClient.lua
--
-- 명령 (모듈 ZedManager.MODULE)
--   C -> S  ReadingRefresh { skill }   클라에서 레벨이 올랐을 때. 서버가 잠시 뒤 서버 쪽 레벨로 다시 계산한다
--                                      (클라가 보낸 레벨은 믿지 않는다: 높은 구간 책 배율을 낮은 레벨에 걸 수 있어서)

require "ZedManagerConfig"
require "TimedActions/ISReadABook"

ZedManager.Reading = ZedManager.Reading or {}
local ZR = ZedManager.Reading
local RM = ZedManager

local function log(msg) RM.log("reading: " .. tostring(msg)) end

local REFRESH_DELAY_MS = 1500   -- 클라 레벨업 보고 뒤 서버가 다시 계산하기까지 (서버 쪽 경험치 동기화를 기다린다)
local SCAN_MS = 10000           -- 놓친 레벨 변화를 잡는 전체 점검 주기

-- 등록된 샌드박스 옵션은 로드 후 항상 값이 있다 (fallback 불필요)
function ZR.enabled()
    return SandboxVars.ZedManager.ReadingBrackets
end

-- 지금 레벨에 배율을 주는 책의 시작 레벨(LvlSkillTrained). 0~1 -> 1, 2~3 -> 3 ... 8~9 -> 9, 10 -> 11(없음)
function ZR.bracketOf(level)
    return level - (level % 2) + 1
end

-- 하한 판정. 켜져 있으면 문맹만 막는다. 꺼져 있으면 바닐라 조건 그대로
function ZR.tooHard(character, item)
    if character:hasTrait(CharacterTrait.ILLITERATE) then return true end
    if ZR.enabled() then return false end
    local sb = SkillBook[item:getSkillTrained()]
    return sb ~= nil and item:getLvlSkillTrained() > character:getPerkLevel(sb.perk) + 1
end

-- ── 책 목록 ───────────────────────────────────────────────
-- books[skill][lvl] = { types = { fullType... }, pages = { [fullType] = n }, maxLevel, maxMult }
-- 같은 구간 책이 여러 종류인 스킬(장검 등)은 그중 가장 많이 읽은 책을 쓴다.
-- SkillBook 은 server/XpSystem/XPSystem_SkillBook.lua 에서 정의돼서 이 파일 로드 때는 없을 수 있다 -> 처음 쓸 때 만든다
local books = nil

local function buildBooks()
    books = {}
    local n = 0
    local all = getScriptManager():getAllItems()
    for i = 0, all:size() - 1 do
        local it = all:get(i)
        local skill = it:getSkillTrained()
        local lvl = it:getLevelSkillTrained()
        local pages = it:getNumberOfPages()
        if skill and skill ~= "" and SkillBook[skill] and lvl and lvl > 0 and pages and pages > 0 then
            local idx = math.floor((lvl + 1) / 2)
            local maxMult = SkillBook[skill]["maxMultiplier" .. tostring(idx)]
            if maxMult then
                books[skill] = books[skill] or {}
                local e = books[skill][lvl]
                if not e then
                    e = { types = {}, pages = {}, maxLevel = it:getMaxLevelTrained(), maxMult = maxMult }
                    books[skill][lvl] = e
                end
                local ft = it:getFullName()
                table.insert(e.types, ft)
                e.pages[ft] = pages
                n = n + 1
            else
                log("book " .. tostring(it:getFullName()) .. " has level " .. tostring(lvl) .. " with no SkillBook maxMultiplier" .. tostring(idx) .. " -> ignored")
            end
        end
    end
    log("indexed " .. tostring(n) .. " skill books")
end

local function getBooks()
    if not books then buildBooks() end
    return books
end

-- 이 스킬의 이 구간 책을 얼마나 읽었는지 (0~1, 같은 구간 여러 종류면 최댓값)
local function readFraction(character, e)
    local best = 0
    for _, ft in ipairs(e.types) do
        local f = character:getAlreadyReadPages(ft) / e.pages[ft]
        if f > best then best = f end
    end
    if best > 1 then best = 1 end
    return best
end

-- ── 배율 갈아 끼우기 (서버·싱글만) ───────────────────────
-- applied["이름:스킬"] = { lvl, mult } 마지막으로 넣은 값. 같으면 다시 보내지 않는다 (force 제외)
local applied = {}

function ZR.refresh(character, skill, why, force)
    if isClient() or not ZR.enabled() then return end
    local sb = SkillBook[skill]
    if not sb or not character or character:isDead() then return end
    local perk = sb.perk
    local level = character:getPerkLevel(perk)
    local lvl = ZR.bracketOf(level)
    local e = getBooks()[skill] and getBooks()[skill][lvl]
    local mult, minL, maxL = 0, 0, 0
    if e then
        mult = math.floor(readFraction(character, e) * 10) * (e.maxMult / 10)   -- 바닐라 checkMultiplier 와 같은 식
        minL, maxL = lvl, e.maxLevel
    end
    local key = tostring(character:getUsername()) .. ":" .. skill
    local rec = applied[key]
    local cur = character:getXp():getMultiplier(perk)
    -- 지금 들고 있는 배율까지 같을 때만 건너뛴다 (캐릭터 초기화 등으로 배율이 남거나 지워진 경우를 다시 맞추려고)
    if not force and rec and rec.lvl == lvl and rec.mult == mult and math.abs(cur - mult) < 0.001 then return end
    if mult > 0 then
        addXpMultiplier(character, perk, mult, minL, maxL)
    elseif cur > 0 then
        addXpMultiplier(character, perk, 0, 0, 0)
    end
    applied[key] = { lvl = lvl, mult = mult }
    if mult > 0 or cur > 0 then
        log(string.format("%s %s level=%d book=%d multiplier %.2f -> %.2f (%s)",
            tostring(character:getUsername()), skill, level, lvl, cur, mult, tostring(why)))
    end
end

function ZR.refreshAll(character, why, force)
    local ok, err = pcall(function()
        for skill, _ in pairs(getBooks()) do ZR.refresh(character, skill, why, force) end
    end)
    if not ok then log("refreshAll FAILED err=" .. tostring(err)) end
end

local function skillOfPerk(perk)
    for skill, sb in pairs(SkillBook) do
        if sb.perk == perk then return skill end
    end
    return nil
end

-- ── 바닐라 ISReadABook 덮어쓰기 ──────────────────────────
local orig = {
    update = ISReadABook.update,
    animEvent = ISReadABook.animEvent,
    checkLevel = ISReadABook.checkLevel,
    checkMultiplier = ISReadABook.checkMultiplier,
    complete = ISReadABook.complete,
}

-- 42.22.2 사본. 바뀐 곳: 첫 SkillBook 분기 조건 -> ZR.tooHard
function ISReadABook:update()
    if not ZR.enabled() then return orig.update(self) end
    self.pageTimer = self.pageTimer + getGameTime():getMultiplier();
    self.item:setJobDelta(self:getJobDelta());
    if not isClient() then
        if self.item:getNumberOfPages() > 0 then
            local pagesRead = math.floor(self.item:getNumberOfPages() * self:getJobDelta())
            self.item:setAlreadyReadPages(pagesRead);
            if self.item:getAlreadyReadPages() > self.item:getNumberOfPages() then
                self.item:setAlreadyReadPages(self.item:getNumberOfPages());
            end
            self.character:setAlreadyReadPages(self.item:getFullType(), self.item:getAlreadyReadPages())
        end
    end
    if SkillBook[self.item:getSkillTrained()] then
        if ZR.tooHard(self.character, self.item) then
            if self.pageTimer >= 200 then
                self.pageTimer = 0;
                if not isClient() then
                    self:doHaloText()
                end
                if self.item:getNumberOfPages() > 0 then
                    self.character:setAlreadyReadPages(self.item:getFullType(), 0)
                    self:forceStop()
                end
            end
        elseif self.item:getMaxLevelTrained() < self.character:getPerkLevel(SkillBook[self.item:getSkillTrained()].perk) + 1 then
            if self.pageTimer >= 200 then
                self.pageTimer = 0;
                if not isClient() then
                    self:doHaloText()
                end
                if self.item:getNumberOfPages() > 0 then
                    self.character:setAlreadyReadPages(self.item:getFullType(), 0)
                    self:forceStop()
                end
            end
        else
            if not isClient() then
                ISReadABook.checkMultiplier(self);
            end
        end
    end

    local stats = self.character:getStats()
    if self.stats and (self.item:getUnhappyChange() < 0.0) then
        if stats:get(CharacterStat.UNHAPPINESS) > self.stats.unhappiness then
            stats:set(CharacterStat.UNHAPPINESS, self.stats.unhappiness)
        end
    end
end

-- 42.22.2 사본. 바뀐 곳: ReadAPage 의 첫 SkillBook 분기 조건 -> ZR.tooHard
function ISReadABook:animEvent(event, parameter)
    if not ZR.enabled() then return orig.animEvent(self, event, parameter) end
    if event == "PageFlip" then
        if getGameSpeed() ~= 1 then
            return
        end
        if self:isBook(self.item) then
            self.character:playSound("PageFlipBook")
        else
            self.character:playSound("PageFlipMagazine")
        end
    end
    if event == "ReadAPage" then
        if isServer() then
            if SkillBook[self.item:getSkillTrained()] then
                if ZR.tooHard(self.character, self.item) then
                    if self.item:getNumberOfPages() > 0 then
                        self.character:setAlreadyReadPages(self.item:getFullType(), 0)
                        self.item:setAlreadyReadPages(0);
                        syncItemFields(self.character, self.item)
                        self.forceStopped = true
                        self.netAction:forceComplete()
                        self:doHaloText()
                        return
                    end
                elseif self.item:getMaxLevelTrained() < self.character:getPerkLevel(SkillBook[self.item:getSkillTrained()].perk) + 1 then
                    self.character:setAlreadyReadPages(self.item:getFullType(), 0)
                    self.item:setAlreadyReadPages(0);
                    syncItemFields(self.character, self.item)
                    self.forceStopped = true
                    self.netAction:forceComplete()
                    self:doHaloText()
                    return
                else
                    ISReadABook.checkMultiplier(self);
                end
            end
            if self.item:getNumberOfPages() > 0 and self.startPage then
                local pagesRead = math.floor(self.item:getNumberOfPages() * self.netAction:getProgress()) + self.startPage;
                self.item:setAlreadyReadPages(pagesRead);
                if self.item:getAlreadyReadPages() > self.item:getNumberOfPages() then
                    self.item:setAlreadyReadPages(self.item:getNumberOfPages());
                    self.netAction:forceComplete()
                end
                self.character:setAlreadyReadPages(self.item:getFullType(), self.item:getAlreadyReadPages())
                syncItemFields(self.character, self.item)
            end
        end
    end
end

-- 42.22.2 사본. 바뀐 곳: 하한 조건 -> ZR.tooHard (하한에 걸리면 읽은 쪽수를 0 으로 되돌리던 것)
function ISReadABook.checkLevel(character, item)
    if not ZR.enabled() then return orig.checkLevel(character, item) end
    if item:getNumberOfPages() <= 0 then
        return
    end
    if not SkillBook[item:getSkillTrained()] then
        return
    end
    if ZR.tooHard(character, item) then
        item:setAlreadyReadPages(0)
        character:setAlreadyReadPages(item:getFullType(), 0)
    end
end

-- 읽는 중 배율 반영: 지금 구간을 맡은 책일 때만 바닐라대로. 다른 구간 책은 읽은 쪽수만 쌓고 배율은 건드리지 않는다
ISReadABook.checkMultiplier = function(self)
    if not ZR.enabled() then return orig.checkMultiplier(self) end
    local sb = SkillBook[self.item:getSkillTrained()]
    if not sb then return end
    if ZR.bracketOf(self.character:getPerkLevel(sb.perk)) ~= self.item:getLvlSkillTrained() then return end
    return orig.checkMultiplier(self)
end

-- 바닐라 complete 는 읽은 책의 최대 배율을 구간과 상관없이 넣는다 -> 끝난 직후 지금 구간 책 배율로 다시 넣는다
function ISReadABook:complete()
    local r = orig.complete(self)
    if ZR.enabled() and not isClient() then
        local skill = self.item:getSkillTrained()
        if SkillBook[skill] then
            local ok, err = pcall(ZR.refresh, self.character, skill, "read complete " .. tostring(self.item:getFullType()), true)
            if not ok then log("refresh after complete FAILED err=" .. tostring(err)) end
        end
    end
    return r
end

-- ── 레벨 변화 감지 ───────────────────────────────────────
-- 경험치가 서버에서 붙으면 서버에서, 클라에서 붙으면 클라에서 LevelPerk 가 난다. 클라는 서버에 다시 계산을 부탁한다
local pending = {}   -- [username] = { player, skills = { [skill]=true }, at }

local function onLevelPerk(character, perk, level, increased)
    if not ZR.enabled() then return end
    local skill = skillOfPerk(perk)
    if not skill then return end
    if isClient() then
        if character == getPlayer() then
            sendClientCommand(character, RM.MODULE, "ReadingRefresh", { skill = skill })
        end
        return
    end
    local ok, err = pcall(ZR.refresh, character, skill, "level " .. tostring(level), false)
    if not ok then log("refresh on level FAILED err=" .. tostring(err)) end
end
Events.LevelPerk.Add(onLevelPerk)

if not isClient() then
    local function onClientCommand(module, command, player, args)
        if module ~= RM.MODULE or command ~= "ReadingRefresh" then return end
        if not ZR.enabled() or not player then return end
        local skill = args and args.skill
        if type(skill) ~= "string" or not SkillBook[skill] then return end
        local key = tostring(player:getUsername())
        local p = pending[key]
        if not p then
            p = { player = player, skills = {} }
            pending[key] = p
        end
        p.skills[skill] = true
        p.at = getTimestampMs() + REFRESH_DELAY_MS
    end
    Events.OnClientCommand.Add(onClientCommand)

    local function players()
        local list = {}
        if isServer() then
            local online = getOnlinePlayers()
            for i = 0, online:size() - 1 do table.insert(list, online:get(i)) end
        else
            local p = getSpecificPlayer(0)
            if p then table.insert(list, p) end
        end
        return list
    end

    local nextScan = 0
    local function onTick()
        local now = getTimestampMs()
        for key, p in pairs(pending) do
            if now >= p.at then
                pending[key] = nil
                for skill, _ in pairs(p.skills) do
                    local ok, err = pcall(ZR.refresh, p.player, skill, "client level up", false)
                    if not ok then log("refresh on client level FAILED err=" .. tostring(err)) end
                end
            end
        end
        if now < nextScan then return end
        nextScan = now + SCAN_MS
        if not ZR.enabled() then return end
        -- 바뀐 것만 보낸다 (applied 와 같으면 건너뜀). 처음 보는 플레이어는 여기서 한 번 맞춰진다
        for _, p in ipairs(players()) do ZR.refreshAll(p, "scan", false) end
    end
    Events.OnTick.Add(onTick)
end

log("loaded (brackets: book 1 = levels 0-1, 2 = 2-3, 3 = 4-5, 4 = 6-7, 5 = 8-9)")
