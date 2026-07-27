-- Bootstrap por-asiento de la imagen "-lazyvim" (bifurcacion, ver Dockerfile.code-ide-neovim-
-- lazyvim). Deliberadamente MINUSCULO: la config real de LazyVim (lua/config, lua/plugins,
-- lazyvim.json) vive en un directorio GLOBAL fuera de /home (/etc/insightbloom/nvim-lazyvim-
-- config, vendorizado en build time), compartido por todos los asientos de un Pod multi-alumno
-- -- igual que /opt/insightbloom/node-global para npm. Este archivo, copiado por
-- sandbox-agent.py (sin cambios; ver NVIM_CONFIG_SOURCE) a "~/.config/nvim/init.lua" de cada
-- asiento, solo referencia esa config global via runtimepath, nunca la duplica.
vim.g.lazyvim_json = "/etc/insightbloom/nvim-lazyvim-config/lazyvim.json"
vim.opt.rtp:prepend("/etc/insightbloom/nvim-lazyvim-config")
require("config.lazy")
