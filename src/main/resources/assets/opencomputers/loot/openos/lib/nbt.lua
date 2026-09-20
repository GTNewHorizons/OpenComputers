local mt = {}
local proxy_cache = setmetatable({}, { __mode = "kv" })
local function is_nbt_tag(t)
  return type(t) == "table" and rawget(t, "__nbt_type") and rawget(t, "__value") ~= nil
end
local function unwrap(data)
  if type(data) ~= "table" then return data end
  return rawget(data, "__raw") or data
end
local function wrap(data)
  if not is_nbt_tag(data) then return data end
  local v = rawget(data, "__value")
  if type(v) ~= "table" then
    return v
  end
  local cached = proxy_cache[data]
  if cached then return cached end
  local proxy = setmetatable({ __raw = data }, mt)
  proxy_cache[data] = proxy
  return proxy
end
local proxy_methods = {}
local nbt_types = {
  string = "string",
  boolean = "boolean",
  byte = "number",
  short = "number",
  int = "number",
  long = "number",
  float = "number",
  double = "number",
  byte_array = "table",
  int_array = "table",
  list = "table",
  compound = "table"
}
for nbt_type, value_type in pairs(nbt_types) do
  proxy_methods["set_" .. nbt_type] = function(t, k, v)
    if type(v) ~= value_type then
      error("value need to be a " .. value_type, 2)
    end
    local raw = unwrap(t).__value
    if type(raw) ~= "table" then
      error("target need to be a compound or list", 2)
    end
    raw[k] = { __nbt_type = nbt_type, __value = v }
    return t
  end
end
mt.__index = function(t, k)
  if proxy_methods[k] then
    return proxy_methods[k]
  end
  local raw = unwrap(t).__value
  local v = raw[k]
  return wrap(v)
end
mt.__newindex = function(t, k, v)
  local raw = unwrap(t).__value
  v = unwrap(v)
  if v == nil then
    raw[k] = v
    return
  end

  local existing = raw[k]
  if is_nbt_tag(existing) then
    if is_nbt_tag(v) then
      raw[k] = v
    else
      existing.__value = v
    end
  else
    if is_nbt_tag(v) then
      raw[k] = v
    elseif type(v) == "boolean" then
      raw[k] = { __nbt_type = "byte", __value = v }
    else
      error("need pass a table with __nbt_type and __value when setting new nbt data", 2)
    end
  end
end
mt.__pairs = function(t)
  local raw = unwrap(t).__value
  return function(_, k)
    local nk, _ = next(raw, k)
    if nk ~= nil then
      return nk, wrap(raw[nk])
    end
  end, raw, nil
end
mt.__len = function(t)
  local raw = unwrap(t).__value
  return #raw
end
local function patch(component)
  if component.__nbt_patched then return component end
  local origin = component
  if not origin.decodeNBT or not origin.encodeNBT then
    io.stderr:write("this component can't be patched\n")
    return origin
  end
  local proxy = setmetatable({ __nbt_patched = true }, { __index = origin })
  proxy.decodeNBT = function(data)
    local result = origin.decodeNBT(data)
    return wrap(result)
  end
  proxy.encodeNBT = function(t)
    return origin.encodeNBT(unwrap(t))
  end
  return proxy
end
return { patch = patch, wrap = wrap, unwrap = unwrap }
