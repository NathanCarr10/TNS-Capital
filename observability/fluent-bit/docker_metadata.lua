-- Names each log line's Compose service and gives every line the same shape:
--   service  the Compose service (app, auth-service, kafka...), VictoriaLogs' stream field
--   level    error, warn, info or debug, whatever the source's format
--   message  the log message
-- Lines from containers outside this Compose project are dropped.

local containers_dir = os.getenv("DOCKER_CONTAINERS_DIR") or "/var/lib/docker/containers"
local project = os.getenv("COMPOSE_PROJECT") or ""

-- Container id -> service name, or false for a container whose lines are dropped
local services = {}

local function service_of(id)
  local known = services[id]
  if known ~= nil then
    return known
  end
  local service = false
  local file = io.open(containers_dir .. "/" .. id .. "/config.v2.json", "r")
  if file then
    local config = file:read("*a")
    file:close()
    local name = config:match('"com%.docker%.compose%.service":"([^"]+)"')
    local owner = config:match('"com%.docker%.compose%.project":"([^"]+)"')
    if name and (project == "" or owner == project) then
      service = name
    end
  end
  services[id] = service
  return service
end

local LEVELS = {
  error = "error", fatal = "error", panic = "error", severe = "error",
  warn = "warn", warning = "warn",
  info = "info", log = "info", notice = "info",
  debug = "debug", trace = "debug", verbose = "debug",
}

local function level_of(record, message)
  -- ECS from Spring Boot: "log.level", or log = {level = ...} when nested; Nest JSON: "level"
  local level = record["log.level"] or record["level"]
  if level == nil and type(record["log"]) == "table" then
    level = record["log"]["level"]
  end
  if level == nil and type(message) == "string" then
    -- Plain text (Kafka "[...] WARN ...", Postgres "ERROR:  ..."): the first level word near the start
    for word in message:sub(1, 120):gmatch("%a+") do
      local found = LEVELS[word:lower()]
      if found and word == word:upper() then
        level = found
        break
      end
    end
  end
  return LEVELS[tostring(level or "info"):lower()] or "info"
end

function enrich(tag, timestamp, record)
  local id = tag:match("containers%.(%x+)%.")
  local service = id and service_of(id)
  if not service then
    return -1, timestamp, record
  end

  local message = record["message"]
  if message == nil and type(record["log"]) == "string" then
    message = record["log"]:gsub("%s+$", "")
    record["log"] = nil
  end
  record["message"] = message or ""
  record["level"] = level_of(record, message)
  record["service"] = service
  -- Docker's receive time duplicates the record's own timestamp (_time in VictoriaLogs)
  record["time"] = nil
  return 2, timestamp, record
end
