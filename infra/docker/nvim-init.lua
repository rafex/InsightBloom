-- Config baseada en la imagen runtime del IDE (infra/docker/Dockerfile.code-ide-runtime).
-- 100% offline por diseno: el pod puede tener internet_enabled=false (DEC-0023), asi que no
-- hay plugin manager (Lazy/Packer) ni ":TSInstall"/"Mason" en runtime -- todos los plugins y
-- parsers de tree-sitter ya vienen vendorizados/instalados en la imagen (ver Dockerfile).

vim.opt.number = true
vim.opt.termguicolors = true
vim.opt.mouse = "a"
vim.opt.expandtab = true
vim.opt.shiftwidth = 4
vim.opt.tabstop = 4

vim.cmd("syntax on")
vim.cmd("filetype plugin indent on")
vim.cmd("colorscheme habamax") -- viene con Neovim 0.10, no requiere plugin

vim.g.mapleader = " "

-- Syntax highlighting via los parsers de tree-sitter instalados por apk (tree-sitter-java,
-- tree-sitter-python, etc, ver Dockerfile) -- usa el tree-sitter nativo de Neovim 0.10
-- (vim.treesitter), sin el plugin nvim-treesitter (que requiere red/compilador en runtime
-- para instalar parsers via :TSInstall).
local ts_langs = { "java", "python", "javascript", "typescript", "json", "html", "css", "bash" }
for _, lang in ipairs(ts_langs) do
  local parser_path = "/usr/lib/tree-sitter/" .. lang .. ".so"
  if vim.uv.fs_stat(parser_path) then
    pcall(vim.treesitter.language.add, lang, { path = parser_path })
  end
end
vim.opt.runtimepath:append("/usr/share/tree-sitter")
vim.api.nvim_create_autocmd("FileType", {
  pattern = ts_langs,
  callback = function(args)
    pcall(vim.treesitter.start, args.buf)
  end,
})

-- Explorador de archivos (nvim-tree)
require("nvim-tree").setup({})
vim.keymap.set("n", "<leader>e", "<cmd>NvimTreeToggle<cr>", { desc = "Explorer" })

-- Autocompletado (nvim-cmp + LuaSnip + cmp-nvim-lsp)
local cmp = require("cmp")
cmp.setup({
  snippet = {
    expand = function(args)
      require("luasnip").lsp_expand(args.body)
    end,
  },
  mapping = cmp.mapping.preset.insert({
    ["<CR>"] = cmp.mapping.confirm({ select = true }),
    ["<Tab>"] = cmp.mapping.select_next_item(),
    ["<S-Tab>"] = cmp.mapping.select_prev_item(),
  }),
  sources = {
    { name = "nvim_lsp" },
  },
})
local lsp_capabilities = require("cmp_nvim_lsp").default_capabilities()

-- LSP de Java (jdtls, instalado via apk -- binario en /usr/bin/jdtls, sin Mason)
vim.api.nvim_create_autocmd("FileType", {
  pattern = "java",
  callback = function()
    local root_dir = require("jdtls.setup").find_root({ ".git", "mvnw", "gradlew", "pom.xml", "build.gradle" })
      or vim.fn.getcwd()
    local workspace_dir = "/home/coder/.cache/jdtls-workspace/" .. vim.fn.fnamemodify(root_dir, ":p:h:t")
    require("jdtls").start_or_attach({
      cmd = { "jdtls", "-data", workspace_dir },
      root_dir = root_dir,
      capabilities = lsp_capabilities,
    })
  end,
})

-- Keymaps LSP basicos (aplican una vez que un language server esta activo en el buffer)
vim.keymap.set("n", "gd", vim.lsp.buf.definition, { desc = "Ir a definicion" })
vim.keymap.set("n", "K", vim.lsp.buf.hover, { desc = "Documentacion" })
vim.keymap.set("n", "<leader>rn", vim.lsp.buf.rename, { desc = "Renombrar" })
vim.keymap.set("n", "<leader>ca", vim.lsp.buf.code_action, { desc = "Code action" })
