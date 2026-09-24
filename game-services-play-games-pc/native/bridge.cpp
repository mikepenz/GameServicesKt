#include "bridge.h"
#include "initialization/initialization.h"
#include "games/recall/client.h"
#include <memory>

using google::play::games::recall::GamesRecallClient;
struct Session {
    std::unique_ptr<GamesRecallClient> recall;
};
// Kotlin serializes operations per handle. Callbacks retain the session until completion.
using Handle = std::shared_ptr<Session>;
extern "C" GS_EXPORT void* gs_pc_create() {
    try { return new Handle(std::make_shared<Session>()); } catch (...) { return nullptr; }
}
extern "C" GS_EXPORT void gs_pc_close(void* handle) { delete static_cast<Handle*>(handle); }
extern "C" GS_EXPORT void gs_pc_request(void* handle, int operation, void* context, gs_pc_callback callback) {
    auto session = *static_cast<Handle*>(handle);
    try {
        if (operation == 0) {
            if (session->recall) { callback(context, 0, 0, ""); return; }
            google::play::initialization::GooglePlayInitialize([session, context, callback](auto result) {
                try {
                    if (result.ok()) session->recall = std::make_unique<GamesRecallClient>();
                    callback(context, 0, static_cast<int>(result.code()), "");
                } catch (...) { callback(context, 0, -2, ""); }
            });
        } else if (!session->recall) {
            callback(context, 0, -1, "");
        } else {
            session->recall->RequestRecallAccess([session, context, callback](auto result) {
                callback(context, 1, static_cast<int>(result.code()), result.ok() ? result.value().recall_session_id.c_str() : "");
            });
        }
    } catch (...) { callback(context, operation, -2, ""); }
}
