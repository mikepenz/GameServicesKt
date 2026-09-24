#ifdef NDEBUG
#undef NDEBUG
#endif
#include "bridge.h"
#include "initialization/internal/initialization.h"
#include "games/recall/internal/client.h"
#include <cassert>
#include <string>
namespace init = google::play::initialization;
namespace recall = google::play::games::recall;
static int destroyed = 0;
static void* initContext;
static init::internal::_InitializeContinuation initComplete;
static void* recallContext;
static recall::internal::_RequestRecallAccessContinuation recallComplete;
extern "C" void _GooglePlayInitialize(void* context, init::internal::_InitializeContinuation continuation) { initContext = context; initComplete = continuation; }
extern "C" void* _GamesRecallClient_Create() { return new int(1); }
extern "C" void _GamesRecallClient_Destroy(void* client) { delete static_cast<int*>(client); ++destroyed; }
extern "C" void _GamesRecallClient_RequestRecallAccess(void*, void* context, recall::internal::_RequestRecallAccessContinuation continuation) { recallContext = context; recallComplete = continuation; }
struct Response { int stage = -1; int code = -99; std::string value; };
static void completed(void* context, int stage, int code, const char* value) {
    auto response = static_cast<Response*>(context);
    response->stage = stage; response->code = code; response->value = value;
}
int main() {
    Response response;
    auto handle = gs_pc_create();
    gs_pc_request(handle, 1, &response, completed);
    assert(response.stage == 0 && response.code == -1);
    gs_pc_request(handle, 0, &response, completed);
    initComplete(initContext, {init::InitializationError::kActionRequiredShutdownClientProcess, ""}, {});
    assert(response.code == 2 && destroyed == 0);
    gs_pc_request(handle, 0, &response, completed);
    initComplete(initContext, {init::InitializationError::kOk, ""}, {});
    assert(response.code == 0);
    gs_pc_request(handle, 1, &response, completed);
    gs_pc_close(handle);
    assert(destroyed == 0); // The pending callback retains its SDK client.
    recallComplete(recallContext, {recall::GamesRecallError::kOk, ""}, {"opaque-session"});
    assert(response.stage == 1 && response.code == 0 && response.value == "opaque-session");
    assert(destroyed == 1);
}
