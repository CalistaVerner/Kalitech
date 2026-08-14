local Boot = require("@builtin/bootstrap/Bootstrap")

-- @builtin/init is the live bootstrap object.  Java and Lua callers invoke
-- methods on the instance directly; no bound procedural facade is exported.
return Boot:createDefault():init()
