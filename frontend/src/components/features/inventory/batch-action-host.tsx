"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import type { Batch, StorageLocation } from "@/services/types";
import type { BatchAction } from "./batch-card";
import { DiscardDialog, EditDialog, MoveDialog, UseDialog } from "./batch-actions";

/** Shared dialog host so any screen can trigger batch actions with one handler. */
export function useBatchActions() {
  const router = useRouter();
  const [state, setState] = useState<{ action: BatchAction; batch: Batch; location?: StorageLocation } | null>(null);
  const onAction = useCallback(
    (action: BatchAction, batch: Batch, location?: StorageLocation) => {
      if (action === "cook") {
        router.push(`/recipes?ingredient=${batch.catalogItemId}&name=${encodeURIComponent(batch.name)}`);
        return;
      }
      setState({ action, batch, location });
    },
    [router],
  );
  const close = () => setState(null);
  const dialogs = (
    <>
      <UseDialog key={`u${state?.batch.id}`} batch={state?.action === "use" ? state.batch : null} onClose={close} />
      <DiscardDialog key={`d${state?.batch.id}`} batch={state?.action === "discard" ? state.batch : null} onClose={close} />
      <MoveDialog key={`m${state?.batch.id}`} batch={state?.action === "move" ? state.batch : null} initial={state?.location} onClose={close} />
      <EditDialog key={`e${state?.batch.id}`} batch={state?.action === "edit" ? state.batch : null} onClose={close} />
    </>
  );
  return { onAction, dialogs };
}
