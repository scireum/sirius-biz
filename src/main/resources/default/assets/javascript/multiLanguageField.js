/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function MultiLanguageField(options) {
    this.FALLBACK_CODE = 'fallback';

    this.defaultLanguage = options.defaultLanguage;
    this.fieldName = options.fieldName;
    this.hasFallback = options.hasFallback || false;
    this.fallbackLabel = options.fallbackLabel;
    this.wrapperId = options.wrapperId;
    this.modalId = options.modalId;
    this.values = options.values;
    this.languageManagementEnabled = options.languageManagementEnabled;
    this.rows = options.rows;
    this.multiline = options.multiline || false;

    this.validLanguages = {};
    if (this.hasFallback) {
        this.validLanguages.fallback = this.fallbackLabel;
    }
    for (let langCode in options.validLanguages) {
        this.validLanguages[langCode] = options.validLanguages[langCode];
    }

    this._wrapper = document.getElementById(this.wrapperId);
    this._input = this._wrapper.querySelector('.mls-input');

    const me = this;

    if (!this.multiline) {
        this._hiddenInputs = this._wrapper.querySelector('.mls-hidden-inputs');
        this._modal = document.getElementById(this.modalId);
        this._modalInputs = this._modal.querySelector('.mls-modal-inputs');
        this._modalPlaceholder = this._modal.querySelector('.mls-modal-placeholder');

        $(me._modal).on('hidden.bs.modal', function () {
            me.updateHiddenFields();
            me.updateOuterInputField();
        })

        this.renderModalBody();
        this.updateHiddenFields();
        this.updateOuterInputField();
        this.updateLanguageManagementOptions();
    } else {
        this._multilineHeader = this._wrapper.querySelector('.mls-tab-header');
        this._multilineContent = this._wrapper.querySelector('.mls-tab-content');
        this._modalPlaceholder = this._wrapper.querySelector('.mls-modal-placeholder');

        this.renderMultilineHeader();
        this.updateLanguageManagementOptions();
    }
}

MultiLanguageField.prototype.renderLanguageRow = function (langCode, langName) {
    const _row = document.createElement('div');
    _row.classList.add('row');
    _row.classList.add('form-group');

    const _labelColumn = document.createElement('div');
    _labelColumn.classList.add('col-md-3');
    _labelColumn.classList.add('mls-language-label');
    _row.appendChild(_labelColumn);

    const _flag = this.renderFlag(langCode);
    _labelColumn.appendChild(_flag);

    const _name = document.createElement('span');
    _name.textContent = langName;
    _labelColumn.appendChild(_name);

    const _inputColumn = document.createElement('div');
    _inputColumn.classList.add('col-md-9');
    _row.appendChild(_inputColumn);

    const _textInput = document.createElement('input');
    _textInput.classList.add('form-control');
    _textInput.type = 'text';
    _textInput.dataset.lang = langCode;
    _textInput.value = this.values[langCode] || '';
    _inputColumn.appendChild(_textInput);

    return _row;
};

MultiLanguageField.prototype.renderLanguageTab = function (langCode, langName, active) {
    const _li = document.createElement('li');
    if (active) {
        _li.classList.add('active');
    }

    const _anchor = document.createElement('a');
    _anchor.classList.add('nav-link');
    _anchor.classList.add('mls-language-label');
    _anchor.href = '#' + this.fieldName + '-' + langCode;
    _anchor.dataset.toggle = 'tab';
    _li.appendChild(_anchor);

    const _flag = this.renderFlag(langCode);
    _anchor.appendChild(_flag);

    const _name = document.createElement('span');
    _name.textContent = langName;
    _anchor.appendChild(_name);

    return _li;
};

MultiLanguageField.prototype.renderLanguageTabInput = function (langCode, langName, active) {
    const _inputColumn = document.createElement('div');
    _inputColumn.classList.add('tab-pane');
    if (active) {
        _inputColumn.classList.add('active');
    }
    _inputColumn.id = this.fieldName + '-' + langCode;

    const _textArea = document.createElement('textarea');
    _textArea.classList.add('form-control');
    _textArea.classList.add('input-block-level');
    _textArea.classList.add('mls-input');
    _textArea.rows = this.rows;

    if (langCode === this.FALLBACK_CODE) {
        _textArea.name = this.fieldName;
    } else {
        _textArea.name = this.fieldName + '-' + langCode;
    }

    _textArea.dataset.lang = langCode;
    _textArea.value = this.values[langCode] || '';
    _inputColumn.appendChild(_textArea);

    return _inputColumn;
};

MultiLanguageField.prototype.renderModalBody = function () {
    this._modal.classList.add('mls-modal');

    let rowsAdded = false;
    for (let langCode in this.validLanguages) {
        let langName = this.validLanguages[langCode];

        if (!this.languageManagementEnabled || langCode === this.FALLBACK_CODE || this.values[langCode]) {
            let _row = this.renderLanguageRow(langCode, langName);
            this._modalInputs.appendChild(_row);
            rowsAdded = true;
        }
    }

    if (this.languageManagementEnabled) {
        if (!rowsAdded) {
            this._modalPlaceholder.classList.remove('hidden');
        }

        const _addLanguageButton = this._modal.querySelector('.mls-add-language-button');
        _addLanguageButton.classList.remove('hidden');

        const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

        for (let langCode in this.validLanguages) {
            let _language = document.createElement('li');
            _language.classList.add('pointer');
            _language.dataset.lang = langCode;
            let _link = document.createElement('a');
            _link.textContent = this.validLanguages[langCode];

            let me = this;
            _link.addEventListener('click', function () {
                let _row = me.renderLanguageTabInput(langCode, me.validLanguages[langCode]);
                //me._modalInputs.appendChild(_row);
                me._modalPlaceholder.classList.add('hidden');
                me.updateLanguageManagementOptions();
            });
            _language.appendChild(_link);
            _addLanguageOptions.appendChild(_language);
        }
    }
}

MultiLanguageField.prototype.renderMultilineHeader = function () {
    // todo switch to dropwdown on more than 4 languages
    let rowsAdded = false;
    for (let langCode in this.validLanguages) {
        let langName = this.validLanguages[langCode];

        if (!this.languageManagementEnabled || langCode === this.FALLBACK_CODE || this.values[langCode]) {
            let active = !rowsAdded;
            let _langTab = this.renderLanguageTab(langCode, langName, active);
            let mlsTabList = this._multilineHeader.querySelector('.mls-tab-list');
            mlsTabList.appendChild(_langTab);

            let _langTabInput = this.renderLanguageTabInput(langCode, langName, active);
            this._multilineContent.appendChild(_langTabInput);

            rowsAdded = true;
        }
    }

    // todo adapt code to fit tabs
    if (this.languageManagementEnabled) {
        if (!rowsAdded) {
            this._modalPlaceholder.classList.remove('hidden');
        }

        const _addLanguageButton = this._multilineHeader.querySelector('.mls-add-language-button');
        _addLanguageButton.classList.remove('hidden');

        const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

        for (let langCode in this.validLanguages) {
            let _language = document.createElement('li');
            _language.classList.add('pointer');
            _language.dataset.lang = langCode;
            let _link = document.createElement('a');
            _link.textContent = this.validLanguages[langCode];

            let me = this;
            _link.addEventListener('click', function () {
                let _languagePane = me.renderLanguageTab(langCode, me.validLanguages[langCode], true);
                me._multilineContent.appendChild(_languagePane);
                me._modalPlaceholder.classList.add('hidden');
                me.updateLanguageManagementOptions();
            });
            _language.appendChild(_link);
            _addLanguageOptions.appendChild(_language);
        }
    }
}

MultiLanguageField.prototype.updateOuterInputField = function () {
    const _fallbackInput = this._modal.querySelector('input[data-lang="fallback"]');
    const _defaultLanguageInput = this._modal.querySelector('input[data-lang="' + this.defaultLanguage + '"]');
    let text;
    if (_defaultLanguageInput) {
        text = _defaultLanguageInput.value;
    }
    if (!text && _fallbackInput) {
        text = _fallbackInput.value;
    }
    this._input.value = text || "";
}

MultiLanguageField.prototype.updateHiddenFields = function () {
    this._hiddenInputs.textContent = '';

    const me = this;
    this._modal.querySelectorAll('input[data-lang]').forEach(function (input) {
        let lang = input.dataset.lang;

        let _hiddenInput = document.createElement('input');
        _hiddenInput.type = 'hidden';
        _hiddenInput.value = input.value;
        _hiddenInput.name = me.fieldName;

        if (lang !== me.FALLBACK_CODE) {
            _hiddenInput.name += '-' + lang;
        }

        me._hiddenInputs.appendChild(_hiddenInput);
    })
}

MultiLanguageField.prototype.updateLanguageManagementOptions = function () {
    if (!this.languageManagementEnabled) {
        return;
    }

    let _addLanguageButton;
    if (this.multiline) {
        _addLanguageButton = this._multilineHeader.querySelector('.mls-add-language-button');
    } else {
        _addLanguageButton = this._modal.querySelector('.mls-add-language-button');
    }

    const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

    if (this.multiline) {
        // todo check and adapt
        this._multilineContent.querySelectorAll('.row').forEach(function (_row) {
            let lang = _row.querySelector('input').dataset.lang;
            let _langOption = _addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
            if (_langOption) {
                _langOption.classList.add('hidden');
            }
        });
    } else {
        this._modalInputs.querySelectorAll('.row').forEach(function (_row) {
            let lang = _row.querySelector('input').dataset.lang;
            let _langOption = _addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
            if (_langOption) {
                _langOption.classList.add('hidden');
            }
        });
    }

    const _selectableOption = _addLanguageOptions.querySelector('li:not(.hidden)');
    if (!_selectableOption) {
        _addLanguageButton.classList.add('hidden');
    }
}

MultiLanguageField.prototype.getLanguageName = function (langCode) {
    return this.validLanguages[langCode];
}

MultiLanguageField.prototype.renderFlag = function (langCode) {
    if (langCode === this.FALLBACK_CODE) {
        // globe icon
        const _globe = document.createElement('img');
        _globe.classList.add('mls-language-flag');
        _globe.classList.add('mls-language-globe');
        _globe.src = '/assets/images/flags/globe.png';
        return _globe;
    } else {
        const _flag = document.createElement('img');
        _flag.classList.add('mls-language-flag');
        _flag.src = '/assets/images/flags/' + langCode + '.png';
        _flag.alt = langCode;
        _flag.title = langCode;
        return _flag;
    }
}
