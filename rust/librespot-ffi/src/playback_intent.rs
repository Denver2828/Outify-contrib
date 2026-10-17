use std::sync::atomic::{AtomicBool, Ordering};

/// Independent of runtime events: a delayed Playing event must not clear a pause.
#[derive(Default)]
pub struct PlaybackIntent {
    paused: AtomicBool,
}

impl PlaybackIntent {
    pub const fn new() -> Self {
        Self {
            paused: AtomicBool::new(false),
        }
    }

    pub fn set_paused(&self, paused: bool) {
        self.paused.store(paused, Ordering::SeqCst);
    }

    pub fn can_play(&self) -> bool {
        !self.paused.load(Ordering::SeqCst)
    }

    pub fn should_resume(&self, was_playing: bool, context_started_playing: bool) -> bool {
        self.can_play() && was_playing && context_started_playing
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pause_blocks_restore_even_before_a_paused_event_arrives() {
        let intent = PlaybackIntent::new();
        intent.set_paused(true);
        assert!(!intent.should_resume(true, true));
        assert!(!intent.can_play());
    }

    #[test]
    fn only_clearing_the_pause_allows_playback_again() {
        let intent = PlaybackIntent::new();
        intent.set_paused(true);
        assert!(!intent.should_resume(true, true));
        intent.set_paused(false);
        assert!(intent.should_resume(true, true));
    }

    #[test]
    fn idle_and_paused_contexts_do_not_autoplay() {
        let intent = PlaybackIntent::new();
        assert!(!intent.should_resume(false, true));
        assert!(!intent.should_resume(true, false));
    }
}
