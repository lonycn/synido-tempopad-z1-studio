#pragma once
#include <atomic>
#include <cstdint>
static_assert(std::atomic<uint32_t>::is_always_lock_free && std::atomic<float>::is_always_lock_free,"Realtime controls must be lock free");
struct tsf;
class Synth {
public:
    enum Type { On, Off, Sustain };
    explicit Synth(const char* path);
    ~Synth();
    bool ready() const { return font!=nullptr && !invalidated.load(std::memory_order_acquire); }
    bool command(Type type,int channel,int key,int velocity);
    void invalidate(){invalidated.store(true,std::memory_order_release);panic();}
    void panic() { epoch.fetch_add(1,std::memory_order_release); }
    void gain(float value) { level.store(value,std::memory_order_relaxed); }
    void render(float* output,int frames);
    int active() const;
private:
    struct Event { Type type; int channel,key,velocity; uint32_t generation; };
    tsf* font=nullptr;
    std::atomic<bool> invalidated{false};
    static constexpr uint32_t Capacity=256;
    Event queue[Capacity];
    std::atomic<uint32_t> write{0},read{0},epoch{1};
    std::atomic<float> level{.45f};
    uint32_t applied=1;
    void silence();
};
