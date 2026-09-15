(() => {
    const teamSelect = document.getElementById('performanceId');
    if (!teamSelect) return;

    // Only a deliberate selection fills the form: preserve saved edits and validation errors on load.
    teamSelect.addEventListener('change', () => {
        const option = teamSelect.selectedOptions[0];
        if (!option || !option.value) return;

        for (const field of ['title', 'startsAt', 'endsAt', 'publishedAt']) {
            const input = teamSelect.form.elements.namedItem(field);
            if (input && option.dataset[field] !== undefined) {
                input.value = option.dataset[field].slice(0, input.maxLength > 0 ? input.maxLength : undefined);
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
            }
        }
    });
})();
