#include "backtrace.h"

#include <stdint.h>
#include <unwind.h>

// 栈回溯辅助结构
struct BacktraceState {
  void **current;
  void **end;
};

// 栈回溯回调
static _Unwind_Reason_Code unwind_callback(struct _Unwind_Context *context, void *arg) {
  struct BacktraceState *state = (struct BacktraceState *)arg;
  uintptr_t pc = _Unwind_GetIP(context);
  if (pc && state->current < state->end) {
    *state->current++ = (void *)pc;
  }
  return state->current < state->end ? _URC_NO_REASON : _URC_END_OF_STACK;
}

// 捕获当前调用栈
int backtrace_capture(void **buffer, int max_frames) {
  struct BacktraceState state = {buffer, buffer + max_frames};
  _Unwind_Backtrace(unwind_callback, &state);
  return state.current - buffer;
}
