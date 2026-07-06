package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.LedgerDayBucket;
import com.custodycalendar.api.domain.model.LedgerEntry;
import com.custodycalendar.api.domain.repository.LedgerEntryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final CaseService caseService;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(CaseService caseService, LedgerEntryRepository ledgerEntryRepository) {
        this.caseService = caseService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> listEntries(UUID caseId) {
        caseService.requireCase(caseId);
        return ledgerEntryRepository.findByCaseIdOrderByDateDescIdAsc(caseId);
    }

    /**
     * Nets all ledger entries into at most one balance per parent pair and
     * day bucket. A positive net means the pair's lower-UUID parent owes the
     * other; the returned record is always oriented debtor -> creditor.
     */
    @Transactional(readOnly = true)
    public List<Balance> computeBalances(UUID caseId) {
        caseService.requireCase(caseId);
        Map<BalanceKey, Integer> net = new LinkedHashMap<>();
        for (LedgerEntry entry : ledgerEntryRepository.findByCaseIdOrderByDateAsc(caseId)) {
            UUID from = entry.getFromParentId();
            UUID to = entry.getToParentId();
            int amount = entry.getAmountDays() == null ? 0 : entry.getAmountDays();
            boolean canonical = from.compareTo(to) < 0;
            BalanceKey key = canonical
                    ? new BalanceKey(from, to, entry.getDayBucket())
                    : new BalanceKey(to, from, entry.getDayBucket());
            net.merge(key, canonical ? amount : -amount, Integer::sum);
        }

        List<Balance> balances = new ArrayList<>();
        for (Map.Entry<BalanceKey, Integer> item : net.entrySet()) {
            int amount = item.getValue();
            if (amount == 0) {
                continue;
            }
            BalanceKey key = item.getKey();
            if (amount > 0) {
                balances.add(new Balance(key.lowParentId(), key.highParentId(), amount, key.dayBucket()));
            } else {
                balances.add(new Balance(key.highParentId(), key.lowParentId(), -amount, key.dayBucket()));
            }
        }
        return balances;
    }

    private record BalanceKey(UUID lowParentId, UUID highParentId, LedgerDayBucket dayBucket) {
    }

    public record Balance(UUID fromParentId, UUID toParentId, int amountDays, LedgerDayBucket dayBucket) {
    }
}
