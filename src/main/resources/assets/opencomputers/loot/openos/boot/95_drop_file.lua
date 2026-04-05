local event = require("event")
local fs = require("filesystem")
local shell = require("shell")

local function onDropFile(ev, _, filename, content)
    local path = shell.resolve(filename)
    local file_parentpath = fs.path(path)
    fs.makeDirectory(file_parentpath)

    if fs.exists(path) then
        if not os.remove(path) then
            io.stderr:write("file already exists")
            return
        end
    end

    local f, reason = io.open(path, "w")
    if not f then
        io.stderr:write("Failed opening file for writing: " .. reason)
        return
    end

    f:write(content)
    f:close()
end

event.listen("drop_file", onDropFile)
