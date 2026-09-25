#include <stdint.h>

// Google's pinned ARM64 C SDK uses these outlined atomic helpers.
// Compile with -mno-outline-atomics so these definitions cannot call themselves.
uint64_t __aarch64_ldadd8_relax(uint64_t value, uint64_t *address) {
    return __atomic_fetch_add(address, value, __ATOMIC_RELAXED);
}

uint64_t __aarch64_ldadd8_acq_rel(uint64_t value, uint64_t *address) {
    return __atomic_fetch_add(address, value, __ATOMIC_ACQ_REL);
}
