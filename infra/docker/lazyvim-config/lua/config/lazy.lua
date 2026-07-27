-- Bootstrap de lazy.nvim + LazyVim. Basado en el starter oficial (LazyVim/starter), con dos
-- cambios para esta imagen 100%-offline (el pod puede tener internet_enabled=false):
--   1. checker.enabled = false -- LazyVim revisa updates de plugins periodicamente por
--      defecto; eso es una llamada de red en runtime que no podemos garantizar.
--   2. "lazypath" se resuelve a stdpath("data").."/lazy/lazy.nvim", y stdpath("data") ya
--      apunta a un directorio GLOBAL (XDG_DATA_HOME=/opt/insightbloom/nvim-lazy/data, ver
--      Dockerfile) donde lazy.nvim y el resto de los plugins ya vienen vendorizados a un
--      commit fijo en build time -- el "fs_stat" de abajo los encuentra y el clone nunca
--      se ejecuta.
local lazypath = vim.fn.stdpath("data") .. "/lazy/lazy.nvim"
if not (vim.uv or vim.loop).fs_stat(lazypath) then
  local lazyrepo = "https://github.com/folke/lazy.nvim.git"
  local out = vim.fn.system({ "git", "clone", "--filter=blob:none", "--branch=stable", lazyrepo, lazypath })
  if vim.v.shell_error ~= 0 then
    vim.api.nvim_echo({
      { "Failed to clone lazy.nvim:\n", "ErrorMsg" },
      { out, "WarningMsg" },
      { "\nPress any key to exit..." },
    }, true, {})
    vim.fn.getchar()
    os.exit(1)
  end
end
vim.opt.rtp:prepend(lazypath)

require("lazy").setup({
  spec = {
    { "LazyVim/LazyVim", import = "lazyvim.plugins" },
    { import = "plugins" },
  },
  defaults = {
    lazy = false,
    version = false,
  },
  -- missing=false: nunca intentar instalar nada en runtime (100% offline). Sin esto, plugins
  -- deshabilitados via `enabled = false` (bufferline/noice/trouble en insightbloom-lite.lua)
  -- igual disparaban un intento de "install missing" al arrancar -- confirmado con un build real
  -- ("...bufferline.nvim.cloning: Permission denied" seguido de un crash en lazy/manage/lock.lua
  -- al no poder escribir el directorio global de solo lectura). Todos los plugins reales ya estan
  -- vendorizados en build time; nada deberia estar "missing" jamas.
  install = { missing = false, colorscheme = { "tokyonight", "habamax" } },
  checker = {
    enabled = false,
    notify = false,
  },
  performance = {
    rtp = {
      -- lazy.nvim resetea 'runtimepath' por defecto (performance.rtp.reset=true) a una lista fija
      -- que NO incluye nuestro rtp:prepend manual de nvim-init-lazyvim.lua -- confirmado con un
      -- build real: "import = 'plugins'" (config/lazy.lua, mas arriba) fallaba con "No specs
      -- found for module 'plugins'" pese a que los archivos existian en disco, porque para cuando
      -- lazy.setup() buscaba esos specs, el reset ya habia borrado el path del runtimepath. Hay
      -- que declararlo aca explicitamente para que sobreviva al reset.
      paths = { "/etc/insightbloom/nvim-lazyvim-config" },
      disabled_plugins = {
        "gzip",
        "tarPlugin",
        "tohtml",
        "tutor",
        "zipPlugin",
      },
    },
  },
})
