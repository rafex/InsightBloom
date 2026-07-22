// Trusted InsightBloom code only. Uploaded presentation artifacts must never
// provide or execute WebAssembly of their own.
__attribute__((export_name("is_valid_until")))
int is_valid_until(long long now_seconds, long long expires_at_seconds) {
  return expires_at_seconds > now_seconds ? 1 : 0;
}
