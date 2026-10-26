type QueryKey = string;
type Listener = () => void;

interface InvalidateOptions {
  queryKey: QueryKey[];
}

class SimpleQueryCache {
  private listeners: Map<QueryKey, Set<Listener>> = new Map();

  subscribe(key: QueryKey, callback: Listener): () => void {
    if (!this.listeners.has(key)) {
      this.listeners.set(key, new Set());
    }
    this.listeners.get(key)!.add(callback);

    return () => {
      this.listeners.get(key)?.delete(callback);
    };
  }

  invalidateQueries(options: InvalidateOptions | QueryKey | QueryKey[]): void {
    let keys: QueryKey[];
    if (Array.isArray(options)) {
      keys = options;
    } else if (typeof options === 'string') {
      keys = [options];
    } else if (options && Array.isArray(options.queryKey)) {
      keys = options.queryKey;
    } else {
      keys = [];
    }

    keys.forEach((key) => {
      const callbacks = this.listeners.get(key);
      if (callbacks) {
        callbacks.forEach((cb) => {
          try {
            cb();
          } catch (err) {
            console.error(`Error executing subscriber for queryKey: ${key}`, err);
          }
        });
      }
      // Broadcast DOM CustomEvent so uncoupled modules or listeners can react instantly
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(`query-invalidate:${key}`));
      }
    });

    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('helixflow:cache-invalidated', { detail: { queryKeys: keys } }));
    }
  }
}

export const queryClient = new SimpleQueryCache();
