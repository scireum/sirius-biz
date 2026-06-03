sirius.ready(function () {
    let tasks = [];
    let elements = [];

    document.querySelectorAll('.lazy-metric-js').forEach(function (_node) {
        elements.push(_node);
        tasks.push({
            provider: _node.dataset['provider'],
            metric: _node.dataset['metric'],
            target: _node.dataset['target'],
            type: _node.dataset['type']
        });
    });

    if (tasks.length > 0) {
        fetch('/tycho/metrics/api', {
            method: "post",
            body: JSON.stringify({tasks: tasks})
        }).then(function (response) {
            return response.json();
        }).then(function (json) {
            for (let i = 0; i < json.tasks.length; i++) {
                const _element = elements[i];
                const task = tasks[i];
                const data = json.tasks[i];

                if (task.type === 'KeyMetric') {
                    _element.querySelector('.metric-value-js').textContent = data.value;
                    if (data.history.length > 0) {
                        inlineChart(_element.querySelector('canvas'), data.history);
                    }
                }
            }
        });
    }
});
