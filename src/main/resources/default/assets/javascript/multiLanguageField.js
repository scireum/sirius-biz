/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function MultiLanguageField(options) {
    this.FALLBACK_CODE = 'fallback';

    this.fieldName = options.fieldName;
    this.hasFallback = options.hasFallback || false;
    this.fallbackLabel = options.fallbackLabel;
    this.wrapperId = options.wrapperId;
    this.modalId = options.modalId;
    this.values = options.values;
    this.languageManagementEnabled = options.languageManagementEnabled;
    this.addLanguageLabel = options.addLanguageLabel;

    this.validLanguages = {};
    if (this.hasFallback) {
        this.validLanguages.fallback = fallbackLabel;
    }
    for (let langCode in options.validLanguages) {
        this.validLanguages[langCode] = options.validLanguages[langCode];
    }

    this._wrapper = document.getElementById(this.wrapperId);
    this._input = this._wrapper.querySelector('.mls-input');
    this._hiddenInputs = this._wrapper.querySelector('.mls-hidden-inputs');
    this._modal = document.getElementById(this.modalId);

    let me = this;
    this._modal.querySelector('.ok-btn').addEventListener('click', function () {
        $(me._modal).modal('hide');
        me.updateHiddenFields();
    });

    this.renderModalBody();
    this.updateHiddenFields();
    this.updateLanguageManagementOptions();
}

MultiLanguageField.prototype.renderLanguageRow = function (langCode, langName) {
    let _row = document.createElement('div');
    _row.classList.add('row');
    _row.classList.add('form-group');

    let _labelColumn = document.createElement('div');
    _labelColumn.classList.add('col-md-3');
    _labelColumn.classList.add('mls-language-label');
    // TODO hacky
    _labelColumn.innerHTML = this.getFlagImage(langCode) + ' ' + langName;
    _row.appendChild(_labelColumn);

    let _inputColumn = document.createElement('div');
    _inputColumn.classList.add('col-md-9');
    _row.appendChild(_inputColumn);

    let _textInput = document.createElement('input');
    _textInput.classList.add('form-control');
    _textInput.type = 'text';
    _textInput.dataset.lang = langCode;
    _textInput.value = this.values[langCode] || '';
    _inputColumn.appendChild(_textInput);

    return _row;
};
MultiLanguageField.prototype.renderModalBody = function () {
    let _inputs = this._modal.querySelector('.mls-modal-inputs');

    for (let langCode in this.validLanguages) {
        let langName = this.validLanguages[langCode];

        let _row = this.renderLanguageRow(langCode, langName);
        _inputs.appendChild(_row);
    }

    if (this.languageManagementEnabled) {
        let _addLanguageButton = this._modal.querySelector('.mls-add-language-button');
        _addLanguageButton.classList.remove('hidden');

        let _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

        for (let langCode in this.validLanguages) {
            let _language = document.createElement('li');
            _language.classList.add('pointer');
            _language.dataset.lang = langCode;
            let _link = document.createElement('a');
            _link.textContent = this.validLanguages[langCode];

            let me = this;
            _link.addEventListener('click', function () {
                let _row = me.renderLanguageRow(langCode, me.validLanguages[langCode]);
                _inputs.appendChild(_row);
                me.updateLanguageManagementOptions();
            });
            _language.appendChild(_link);
            _addLanguageOptions.appendChild(_language);
        }
    }
}

MultiLanguageField.prototype.updateHiddenFields = function () {
    this._hiddenInputs.textContent = '';

    let me = this;
    this._modal.querySelectorAll('input[data-lang]').forEach(function (input) {
        let lang = input.dataset.lang;

        let _hiddenInput = document.createElement('input');
        _hiddenInput.type = 'hidden';
        _hiddenInput.value = input.value;
        _hiddenInput.name = me.fieldName;

        if (me.hasFallback && lang === me.FALLBACK_CODE) {
            // also update main field
            me._input.value = input.value;
        } else {
            _hiddenInput.name += '-' + lang;

        }

        me._hiddenInputs.appendChild(_hiddenInput);
    })
}

MultiLanguageField.prototype.updateLanguageManagementOptions = function () {
    if (!this.languageManagementEnabled) {
        return;
    }

    let _addLanguageButton = this._modal.querySelector('.mls-add-language-button');

    let _inputs = this._modal.querySelector('.mls-modal-inputs');
    let _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');
    
    _inputs.querySelectorAll('.row').forEach(function (_row) {
        let lang = _row.querySelector('input').dataset.lang;
        let _langOption = _addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
        if (_langOption) {
            _langOption.classList.add('hidden');
        }
    });
    
    let _selectableOption = _addLanguageOptions.querySelector('li:not(.hidden)');
    if (!_selectableOption) {
        _addLanguageButton.classList.add('hidden');
    }
}

MultiLanguageField.prototype.getLanguageName = function (langCode) {
    return this.validLanguages[langCode];
}

MultiLanguageField.prototype.getFlagImage = function (langCode) {
    if (langCode === this.FALLBACK_CODE) {
        // globe icon
        return String.fromCodePoint(127758);
    } else {
        return '<img class="mls-language-flag" src=\'/assets/images/flags/' + langCode + '.png\' alt=\'' + langCode + ' \'/>';
    }
}
